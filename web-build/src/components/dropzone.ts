// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

import Alpine from 'alpinejs';
import { UploadBatch, UploadBatchState } from '../models/UploadBatch';
import { humanFileSize } from '../utils';

export const Dropzone = () => ({
    activeBatch: null as UploadBatch | null,
    dragActive: false,
    isProcessing: false,
    processingName: '',
    processingProgress: 0,
    isSubmitting: false,
    errorMessage: '',

    get isUploading(): boolean {
        return this.activeBatch?.state === UploadBatchState.UPLOADING;
    },

    get files() {
        return this.activeBatch?.files || [];
    },

    init() { this.createNewBatch(); },

    createNewBatch() {
        const appStore = Alpine.store('app') as any;
        this.activeBatch = appStore.createBatch();
    },

    async handleFiles(fileList: FileList | File[]) {
        if (!this.activeBatch || this.activeBatch.state !== UploadBatchState.DRAFT || this.isProcessing) return;
        this.errorMessage = '';
        this.isProcessing = true;
        try {
            for (const file of Array.from(fileList)) {
                this.processingName = file.name;
                this.processingProgress = 0;
                try {
                    const added = await this.activeBatch.addFile(file, (fraction: number) => {
                        this.processingProgress = fraction;
                    });
                    if (!added) console.info('Skipped duplicate or canceled file: ' + file.name);
                } catch (error) {
                    console.error('Could not add file "' + file.name + '":', error);
                    this.errorMessage = 'Could not check ' + file.name + ': ' + (error as Error).message;
                }
            }
        } finally {
            this.isProcessing = false;
            this.processingName = '';
            this.processingProgress = 0;
        }
    },

    dropFile(event: DragEvent) {
        this.dragActive = false;
        if (event.dataTransfer?.files) void this.handleFiles(event.dataTransfer.files);
    },

    removeFile(index: number) {
        const file = this.activeBatch?.files[index];
        if (file) this.activeBatch?.removeFile(file.checksum);
    },

    async submit() {
        const batch = this.activeBatch;
        if (!batch || batch.files.length === 0 || this.isProcessing || this.isSubmitting) return;
        this.errorMessage = '';
        this.isSubmitting = true;
        try {
            await batch.initialize();
            const decision = await batch.waitForDecision();
            if (decision === UploadBatchState.ACCEPTED) {
                (Alpine.store('app') as any).showUploadModal = false;
                this.createNewBatch();
                await batch.start();
            } else if (decision === UploadBatchState.REJECTED) {
                batch.state = UploadBatchState.DRAFT;
                this.errorMessage = 'The receiving device declined this upload. You can try again.';
            } else {
                throw new Error('The upload request ended unexpectedly.');
            }
        } catch (error) {
            this.errorMessage = (error as Error).message || 'Could not start upload.';
            console.error('Submit failed:', error);
        } finally {
            this.isSubmitting = false;
        }
    },

    humanFileSize(size: number) { return humanFileSize(size); },
    dragOver() { this.dragActive = true; },
    dragLeave() { this.dragActive = false; }
});
