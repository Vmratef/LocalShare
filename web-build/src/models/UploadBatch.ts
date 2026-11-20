// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later


import Alpine from 'alpinejs';
import { sha256 } from 'js-sha256';
import { 
    UploadFile, 
    UploadFileState, 
    WebActionInitializeFileUpload, 
    WebEventUploadAccepted, 
    WebEventUploadDenied, 
    WebEventUploadUpdated 
} from '../types';
import { parseJSON } from '../utils';
import { apiActionRequest } from '../services/api';

export enum UploadBatchState {
    DRAFT,
    REQUESTED,
    REJECTED,
    ACCEPTED,
    UPLOADING,
    FINISHED,
    SUCCESS,
    ERROR,
    CANCELED
}

let nextDraftId = 1;

// Size of the slices that are read and hashed one at a time.
// Keeps memory usage small no matter how large the file is.
const HASH_CHUNK_SIZE = 8 * 1024 * 1024; // 8 MiB

export class UploadBatch {
    private sse: EventSource;
    files: UploadFile[] = [];
    state: UploadBatchState = UploadBatchState.DRAFT;
    draftId: number;
    id?: string;
    private abortController?: AbortController;
    private hashingControllers = new Map<string, AbortController>();

    private handlers = {
        accepted: (e: MessageEvent) => {
            Alpine.reactive(this).onUploadAccepted(parseJSON<WebEventUploadAccepted>(e.data))
        },
        denied: (e: MessageEvent) => {
            Alpine.reactive(this).onUploadDenied(parseJSON<WebEventUploadDenied>(e.data))
        },
        updated: (e: MessageEvent) => {
            Alpine.reactive(this).onUploadUpdated(parseJSON<WebEventUploadUpdated>(e.data))
        }
    };

    constructor(sse: EventSource){
        this.sse = sse;
        this.draftId = nextDraftId++;
        this.addSSEEventListeners();
    }

    dispose() {
        this.abortUpload();
        this.removeSSEEventListeners();
    }

    private addSSEEventListeners() {

        this.sse.addEventListener('upload_accepted', this.handlers.accepted);
        this.sse.addEventListener('upload_denied', this.handlers.denied);
        this.sse.addEventListener('upload_updated', this.handlers.updated);
    }

    private removeSSEEventListeners() {
        this.sse.removeEventListener('upload_accepted', this.handlers.accepted);
        this.sse.removeEventListener('upload_denied', this.handlers.denied);
        this.sse.removeEventListener('upload_updated', this.handlers.updated);
    }

    /**
     * Calculates the SHA-256 of a file incrementally.
     *
     * The old implementation called `file.arrayBuffer()`, which loads the WHOLE file
     * into memory. For large files that fails (out of memory / buffer too big), the
     * promise rejects, and the file never shows up in the list.
     *
     * Here the file is read in small slices, so memory stays low and the browser
     * stays responsive. The resulting hex string is identical to the old one, so the
     * server-side checksum verification keeps working unchanged.
     */
    async calculateChecksum(file: File, onProgress?: (fraction: number) => void, signal?: AbortSignal): Promise<string> {
        const hasher = sha256.create();

        for (let offset = 0; offset < file.size; offset += HASH_CHUNK_SIZE) {
            if (signal?.aborted) throw new DOMException('Hashing canceled', 'AbortError');
            const end = Math.min(offset + HASH_CHUNK_SIZE, file.size);
            const chunk = await file.slice(offset, end).arrayBuffer();
            if (signal?.aborted) throw new DOMException('Hashing canceled', 'AbortError');
            hasher.update(chunk);

            onProgress?.(end / file.size);

            // Give the browser a chance to repaint / handle events between chunks.
            await new Promise<void>(resolve => setTimeout(resolve, 0));
        }

        if (signal?.aborted) throw new DOMException('Hashing canceled', 'AbortError');
        onProgress?.(1);
        return hasher.hex();
    }

    async addFile(file: File, onProgress?: (fraction: number) => void) {
        if (this.state !== UploadBatchState.DRAFT) return;

        const mime = file.type || 'application/octet-stream';
        const previewUrl = (mime.startsWith('image/') || mime.startsWith('video/'))
            ? URL.createObjectURL(file)
            : undefined;

        // Add the file to the reactive list BEFORE hashing. Large files can take
        // minutes to hash; the user should see the file immediately, not an empty
        // dropzone that looks as if the drop was ignored.
        const pendingFile: UploadFile = {
            checksum: `pending-${this.draftId}-${Date.now()}-${this.files.length}`,
            file: file,
            previewUrl: previewUrl,
            name: file.name,
            mimeType: mime,
            size: file.size,
            state: UploadFileState.PENDING,
            progress: 0
        };
        this.files.push(pendingFile);
        const hashingController = new AbortController();
        this.hashingControllers.set(pendingFile.checksum, hashingController);

        try {
            const checksum = await this.calculateChecksum(file, onProgress, hashingController.signal);
            // Hashing may take a while; check duplicates only after the real hash
            // is available, then replace the temporary identifier in-place.
            if (this.files.some(existing => existing !== pendingFile && existing.checksum === checksum)) {
                if (pendingFile.previewUrl) URL.revokeObjectURL(pendingFile.previewUrl);
                this.files = this.files.filter(existing => existing !== pendingFile);
                return;
            }
            pendingFile.checksum = checksum;
            this.files = [...this.files];
        } catch (error) {
            if (pendingFile.previewUrl) URL.revokeObjectURL(pendingFile.previewUrl);
            this.files = this.files.filter(existing => existing !== pendingFile);
            if ((error as any)?.name !== 'AbortError') throw error;
        } finally {
            this.hashingControllers.delete(pendingFile.checksum);
        }
    }

    removeFile(checksum: string) {
        this.hashingControllers.get(checksum)?.abort();
        this.hashingControllers.delete(checksum);
        const removed = this.files.find(f => f.checksum === checksum);
        if (removed?.previewUrl) URL.revokeObjectURL(removed.previewUrl);
        this.files = this.files.filter(f => f.checksum !== checksum);
    }

    async initialize() {
        if (this.state !== UploadBatchState.DRAFT) return;
        this.state = UploadBatchState.REQUESTED;

        try {
            await apiActionRequest({
                type: 'initialize_file_upload',
                draftId: this.draftId,
                files: this.files.map(f => ({
                    checksum: f.checksum,
                    name: f.name,
                    mimeType: f.mimeType,
                    size: f.size
                }))
            } as WebActionInitializeFileUpload);
        } catch (err) {
            this.state = UploadBatchState.ERROR;
            throw err;
        }
    }

    async start() {
        if (this.state !== UploadBatchState.ACCEPTED) return;
        this.state = UploadBatchState.UPLOADING;

        this.abortController = new AbortController();
        const signal = this.abortController.signal;

        const uploadPromises = this.files.map(file => this.uploadSingleFile(file, signal));
        await Promise.allSettled(uploadPromises);
        this.state = UploadBatchState.FINISHED;
    }

    private async uploadSingleFile(file: UploadFile, signal: AbortSignal): Promise<void> {
        file.progress = 0;
        file.state = UploadFileState.UPLOADING;

        try {
            const response = await fetch(`/${window.token}/upload/${this.id}/${file.checksum}`, {
                method: 'POST',
                body: file.file,
                headers: { 'Content-Type': file.mimeType },
                signal: signal
            });

            if (!response.ok) throw new Error(await response.text());
            // file.state = UploadFileState.FINISHED;
            // file.progress = file.size;
        } catch (err: any) {
            if (err.name === 'AbortError') {
                file.state = UploadFileState.CANCELED;
            } else {
                file.state = UploadFileState.ERROR;
                console.error(`Upload failed for ${file.name}:`, err);
            }
        }
    }

    abortUpload() {
        this.abortController?.abort();
        if (this.state === UploadBatchState.UPLOADING) {
            this.state = UploadBatchState.CANCELED;
        }
    }

    private onUploadAccepted(event: WebEventUploadAccepted) {
        if (this.state !== UploadBatchState.REQUESTED || this.draftId !== event.draftId) return;
        this.id = event.id;
        this.state = UploadBatchState.ACCEPTED;
        // Auto-start when accepted? Or wait for user?
        // Let's assume the UI will call start()
    }

    private onUploadDenied(event: WebEventUploadDenied) {
        if (this.draftId !== event.draftId) return;
        this.state = UploadBatchState.REJECTED;
    }

    private onUploadUpdated(event: WebEventUploadUpdated) {
        if (this.id !== event.id) return;

        const file = this.files.find(f => f.checksum === event.fileChecksum);
        if (!file) return;

        file.progress = event.progress;

        if (event.message) {
            file.errorMessage = event.message;
        }

        switch (event.status) {
            case 'pending': file.state = UploadFileState.PENDING; break;
            case 'uploading': file.state = UploadFileState.UPLOADING; break;
            case 'finished': file.state = UploadFileState.CONFIRMED; break;
            case 'error': file.state = UploadFileState.ERROR; break;
            case 'canceled': file.state = UploadFileState.CANCELED; break;
        }

        this.files = [...this.files];
    }
}
