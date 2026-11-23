// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

import Alpine from 'alpinejs';
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

export class UploadBatch {
    private sse: EventSource;
    files: UploadFile[] = [];
    state: UploadBatchState = UploadBatchState.DRAFT;
    draftId: number;
    id?: string;
    private abortController?: AbortController;
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

    async addFile(file: File): Promise<boolean> {
        if (this.state !== UploadBatchState.DRAFT) return false;
        const mime = file.type || 'application/octet-stream';
        const previewUrl = (mime.startsWith('image/') || mime.startsWith('video/')) ? URL.createObjectURL(file) : undefined;
        // This is an upload identifier, not a content hash. Avoid reading the file before upload.
        const checksum = 'file-' + this.draftId + '-' + Date.now() + '-' +
            Math.random().toString(36).slice(2) + '-' + this.files.length;
        const uploadFile: UploadFile = {
            checksum, file, previewUrl, name: file.name, mimeType: mime, size: file.size,
            state: UploadFileState.PENDING, progress: 0
        };
        this.files = [...this.files, uploadFile];
        return true;
    }

    removeFile(checksum: string) {
        const removed = this.files.find(file => file.checksum === checksum);
        if (!removed) return;
        if (removed.previewUrl) URL.revokeObjectURL(removed.previewUrl);
        this.files = this.files.filter(file => file.checksum !== checksum);
    }

    async initialize() {
        if (this.state !== UploadBatchState.DRAFT) return;
        if (this.files.length === 0) throw new Error('Add at least one file before uploading.');
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
