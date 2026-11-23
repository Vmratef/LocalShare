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
    DRAFT, REQUESTED, REJECTED, ACCEPTED, UPLOADING, FINISHED, SUCCESS, ERROR, CANCELED
}

let nextDraftId = 1;
const HASH_CHUNK_SIZE = 4 * 1024 * 1024; // 4 MiB: bounded memory, compatible with older WebViews.

export class UploadBatch {
    private sse: EventSource;
    files: UploadFile[] = [];
    state: UploadBatchState = UploadBatchState.DRAFT;
    draftId: number;
    id?: string;
    private abortController?: AbortController;
    private hashingControllers = new Map<string, AbortController>();
    private decisionResolver?: (state: UploadBatchState) => void;

    private handlers = {
        accepted: (e: MessageEvent) => Alpine.reactive(this).onUploadAccepted(parseJSON<WebEventUploadAccepted>(e.data)),
        denied: (e: MessageEvent) => Alpine.reactive(this).onUploadDenied(parseJSON<WebEventUploadDenied>(e.data)),
        updated: (e: MessageEvent) => Alpine.reactive(this).onUploadUpdated(parseJSON<WebEventUploadUpdated>(e.data))
    };

    constructor(sse: EventSource) {
        this.sse = sse;
        this.draftId = nextDraftId++;
        this.addSSEEventListeners();
    }

    dispose() {
        this.abortUpload();
        this.hashingControllers.forEach(controller => controller.abort());
        this.hashingControllers.clear();
        this.files.forEach(file => { if (file.previewUrl) URL.revokeObjectURL(file.previewUrl); });
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

    private readChunk(blob: Blob, signal: AbortSignal): Promise<ArrayBuffer> {
        if (signal.aborted) return Promise.reject(new DOMException('Hashing canceled', 'AbortError'));
        if (typeof blob.arrayBuffer === 'function') {
            return blob.arrayBuffer().then(buffer => {
                if (signal.aborted) throw new DOMException('Hashing canceled', 'AbortError');
                return buffer;
            });
        }

        // FileReader is the fallback for older Android System WebView versions.
        return new Promise<ArrayBuffer>((resolve, reject) => {
            const reader = new FileReader();
            const cleanup = () => signal.removeEventListener('abort', onAbort);
            const onAbort = () => reader.abort();
            signal.addEventListener('abort', onAbort, { once: true });
            reader.onload = () => {
                cleanup();
                if (reader.result instanceof ArrayBuffer) resolve(reader.result);
                else reject(new Error('Could not read file chunk.'));
            };
            reader.onerror = () => {
                cleanup();
                reject(reader.error || new Error('Could not read file chunk.'));
            };
            reader.onabort = () => {
                cleanup();
                reject(new DOMException('Hashing canceled', 'AbortError'));
            };
            reader.readAsArrayBuffer(blob);
        });
    }

    async calculateChecksum(file: File, onProgress?: (fraction: number) => void, signal?: AbortSignal): Promise<string> {
        const hasher = sha256.create();
        const abortSignal = signal || new AbortController().signal;
        for (let offset = 0; offset < file.size; offset += HASH_CHUNK_SIZE) {
            if (abortSignal.aborted) throw new DOMException('Hashing canceled', 'AbortError');
            const end = Math.min(offset + HASH_CHUNK_SIZE, file.size);
            const chunk = await this.readChunk(file.slice(offset, end), abortSignal);
            hasher.update(new Uint8Array(chunk));
            onProgress?.(end / Math.max(file.size, 1));
            await new Promise<void>(resolve => setTimeout(resolve, 0));
        }
        if (abortSignal.aborted) throw new DOMException('Hashing canceled', 'AbortError');
        onProgress?.(1);
        return hasher.hex();
    }

    async addFile(file: File, onProgress?: (fraction: number) => void): Promise<boolean> {
        if (this.state !== UploadBatchState.DRAFT) return false;
        const mime = file.type || 'application/octet-stream';
        const previewUrl = (mime.startsWith('image/') || mime.startsWith('video/')) ? URL.createObjectURL(file) : undefined;
        const pendingFile: UploadFile = {
            checksum: 'pending-' + this.draftId + '-' + Date.now() + '-' + this.files.length,
            file, previewUrl, name: file.name, mimeType: mime, size: file.size,
            state: UploadFileState.PENDING, progress: 0
        };

        // Show the file immediately while its checksum is calculated.
        this.files = [...this.files, pendingFile];
        const controller = new AbortController();
        const pendingChecksum = pendingFile.checksum;
        this.hashingControllers.set(pendingChecksum, controller);
        try {
            const checksum = await this.calculateChecksum(file, onProgress, controller.signal);
            if (!this.files.some(existing => existing.checksum === pendingChecksum)) return false;
            if (this.files.some(existing => existing.checksum === checksum)) {
                if (pendingFile.previewUrl) URL.revokeObjectURL(pendingFile.previewUrl);
                this.files = this.files.filter(existing => existing.checksum !== pendingChecksum);
                return false;
            }
            pendingFile.checksum = checksum;
            this.files = [...this.files];
            return true;
        } catch (error) {
            if (pendingFile.previewUrl) URL.revokeObjectURL(pendingFile.previewUrl);
            this.files = this.files.filter(existing => existing.checksum !== pendingChecksum);
            if ((error as any)?.name === 'AbortError') return false;
            throw error;
        } finally {
            this.hashingControllers.delete(pendingChecksum);
        }
    }

    removeFile(checksum: string) {
        const removed = this.files.find(file => file.checksum === checksum);
        if (!removed) return;
        this.hashingControllers.get(checksum)?.abort();
        this.hashingControllers.delete(checksum);
        if (removed.previewUrl) URL.revokeObjectURL(removed.previewUrl);
        this.files = this.files.filter(file => file !== removed);
    }

    async initialize() {
        if (this.state !== UploadBatchState.DRAFT) return;
        if (this.files.length === 0) throw new Error('Add at least one file before uploading.');
        if (this.hashingControllers.size > 0 || this.files.some(file => file.checksum.startsWith('pending-'))) {
            throw new Error('Please wait until file checks finish.');
        }
        this.state = UploadBatchState.REQUESTED;
        try {
            await apiActionRequest({
                type: 'initialize_file_upload',
                draftId: this.draftId,
                files: this.files.map(file => ({
                    checksum: file.checksum, name: file.name, mimeType: file.mimeType, size: file.size
                }))
            } as WebActionInitializeFileUpload);
        } catch (error) {
            this.state = UploadBatchState.DRAFT;
            throw error;
        }
    }

    waitForDecision(): Promise<UploadBatchState> {
        if (this.state !== UploadBatchState.REQUESTED) return Promise.resolve(this.state);
        return new Promise(resolve => { this.decisionResolver = resolve; });
    }

    private resolveDecision() {
        const resolve = this.decisionResolver;
        this.decisionResolver = undefined;
        resolve?.(this.state);
    }

    async start() {
        if (this.state !== UploadBatchState.ACCEPTED) return;
        this.state = UploadBatchState.UPLOADING;
        this.abortController = new AbortController();
        await Promise.allSettled(this.files.map(file => this.uploadSingleFile(file, this.abortController!.signal)));
        if (this.state !== UploadBatchState.CANCELED) this.state = UploadBatchState.FINISHED;
    }

    private async uploadSingleFile(file: UploadFile, signal: AbortSignal): Promise<void> {
        file.progress = 0;
        file.state = UploadFileState.UPLOADING;
        try {
            const response = await fetch('/' + window.token + '/upload/' + this.id + '/' + file.checksum, {
                method: 'POST', body: file.file,
                headers: { 'Content-Type': file.mimeType }, signal
            });
            if (!response.ok) throw new Error(await response.text());
        } catch (error: any) {
            if (error?.name === 'AbortError') file.state = UploadFileState.CANCELED;
            else {
                file.state = UploadFileState.ERROR;
                file.errorMessage = error?.message || 'Upload failed.';
                console.error('Upload failed for ' + file.name + ':', error);
            }
            this.files = [...this.files];
        }
    }

    abortUpload() {
        this.abortController?.abort();
        if (this.state === UploadBatchState.UPLOADING) this.state = UploadBatchState.CANCELED;
    }

    private onUploadAccepted(event: WebEventUploadAccepted) {
        if (this.state !== UploadBatchState.REQUESTED || this.draftId !== event.draftId) return;
        this.id = event.id;
        this.state = UploadBatchState.ACCEPTED;
        this.resolveDecision();
    }

    private onUploadDenied(event: WebEventUploadDenied) {
        if (this.draftId !== event.draftId || this.state !== UploadBatchState.REQUESTED) return;
        this.state = UploadBatchState.REJECTED;
        this.resolveDecision();
    }

    private onUploadUpdated(event: WebEventUploadUpdated) {
        if (this.id !== event.id) return;
        const file = this.files.find(item => item.checksum === event.fileChecksum);
        if (!file) return;
        if (typeof event.progress === 'number') file.progress = event.progress;
        if (event.message) file.errorMessage = event.message;
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
