// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

import Alpine from 'alpinejs';
import { UploadBatch, UploadBatchState } from '../models/UploadBatch';
import { humanFileSize } from '../utils';

export const Dropzone = () => ({
    activeBatch: null as UploadBatch | null,
    dragActive: false,
    trigger: 0,

    // Set while files are being read/hashed (can take a while for large files).
    // Can be used in the template, e.g. x-show="isProcessing" / x-text="processingName".
    isProcessing: false,
    processingName: '',
    processingProgress: 0, // 0..1

    get isUploading(): boolean {
        return this.activeBatch?.state === UploadBatchState.UPLOADING;
    },

    get files() {
        this.trigger
        return this.activeBatch?.files || [];
    },

    init() {
        // Start with a fresh draft batch
        this.createNewBatch();
    },

    createNewBatch() {
        const appStore = Alpine.store('app') as any;
        this.activeBatch = appStore.createBatch();
    },

    async handleFiles(fileList: FileList | File[]) {
        if (!this.activeBatch) this.createNewBatch();

        // Copy first: a FileList from a drop event must be read synchronously.
        const files = Array.from(fileList);
        const failed: string[] = [];

        this.isProcessing = true;
        try {
            for (const file of files) {
                this.processingName = file.name;
                this.processingProgress = 0;

                try {
                    await this.activeBatch!.addFile(file, (fraction: number) => {
                        this.processingProgress = fraction;
                    });
                } catch (err) {
                    // One bad file (too big for the browser, a dropped folder, ...)
                    // must not break the whole drop.
                    console.error(`Could not add file "${file.name}":`, err);
                    failed.push(file.name);
                }
            }
        } finally {
            this.isProcessing = false;
            this.processingName = '';
            this.processingProgress = 0;
        }

        if (failed.length > 0) {
            alert(`These files could not be read and were skipped:\n${failed.join('\n')}`);
        }
    },

    dropFile(e: DragEvent) {
        this.dragActive = false;
        if (e.dataTransfer?.files) {
            this.handleFiles(e.dataTransfer.files);
        }
    },

    removeFile(index: number) {
        if (!this.activeBatch) return;
        const file = this.activeBatch.files[index];
        if (file) {
            this.activeBatch.removeFile(file.checksum as string);
        }
    },

    async submit() {
        if (!this.activeBatch || this.activeBatch.files.length === 0) return;

        try {
            // 1. Initialize (Ask for permission/ID)
            await this.activeBatch.initialize();
            
            // Note: The UI might need to wait for 'upload_accepted' SSE event 
            // which changes state to ACCEPTED. 
            // We can poll or watch for state change here.
            
            const checkState = () => {
                if (this.activeBatch?.state === UploadBatchState.ACCEPTED) {
                    this.activeBatch.start();
                    // Close the modal when accepted
                    (Alpine.store('app') as any).showUploadModal = false;
                    // Reset for next time
                    this.createNewBatch();
                } else if (this.activeBatch?.state === UploadBatchState.REJECTED) {
                    // No alert anymore, UI handles showing rejected state
                    this.activeBatch.state = UploadBatchState.DRAFT; 
                } else if (this.activeBatch?.state === UploadBatchState.REQUESTED) {
                    setTimeout(checkState, 500);
                }
            };
            
            checkState();

        } catch (err) {
            console.error('Submit failed:', err);
            alert('Could not start upload.');
        }
    },

    humanFileSize(size: number) { return humanFileSize(size); },

    dragOver() { this.dragActive = true; },
    dragLeave() { this.dragActive = false; }
});
