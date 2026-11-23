// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

import Alpine from 'alpinejs';
import { UploadBatch, UploadBatchState } from '../models/UploadBatch';
import { humanFileSize } from '../utils';

export const Dropzone = () => ({
    activeBatch: null as UploadBatch | null,
    dragActive: false,
    isSubmitting: false,
    errorMessage: '',

    get isUploading(): boolean {
        return this.activeBatch?.state === UploadBatchState.UPLOADING;
    },

    get files() {
        return this.activeBatch?.files || [];
    },

    init() { this.createNewBatch(); (window as any).LocalShareDebug?.log('info', 'Dropzone initialized', { batchReady: !!this.activeBatch, state: this.activeBatch?.state }); },

    onFileInputChange(event: Event) {
        const input = event.target as HTMLInputElement;
        const files = Array.from(input.files || []);
        (window as any).LocalShareDebug?.log('info', 'File picker change received', { count: files.length, files: files.map(file => ({ name: file.name, size: file.size, type: file.type, lastModified: file.lastModified })) });
        // Snapshot File objects before resetting the input so selecting the same file works again.
        void this.handleFiles(files);
        input.value = '';
    },

    createNewBatch() {
        const appStore = Alpine.store('app') as any;
        this.activeBatch = appStore.createBatch();
    },

    async handleFiles(fileList: FileList | File[]) {
        this.errorMessage = '';
        const selectedFiles = Array.from(fileList || []);
        if (selectedFiles.length === 0) return;

        // Recover automatically if the current draft was already submitted or not initialized.
        if (!this.activeBatch || this.activeBatch.state !== UploadBatchState.DRAFT) {
            this.createNewBatch();
        }
        const batch = this.activeBatch;
        if (!batch) {
            this.errorMessage = 'The upload list could not be initialized. Close and reopen the upload window.';
            return;
        }

        for (const file of selectedFiles) {
            try {
                const added = await batch.addFile(file);
                if (!added) throw new Error('The current upload draft is not ready.');
            } catch (error) {
                console.error('Could not add file "' + file.name + '":', error);
                this.errorMessage = 'Could not add ' + file.name + ': ' + ((error as Error).message || 'Unknown error');
            }
        }

        // Refresh the reactive array explicitly for browsers with older FileList/reactivity behavior.
        batch.files = [...batch.files];
        if (batch.files.length === 0 && !this.errorMessage) {
            this.errorMessage = 'No files were added. Please try selecting the file again.';
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

    async sendDirect() {
        const files = this.activeBatch?.files.map(item => item.file) || [];
        if (files.length === 0) return;
        const rtc = Alpine.store('rtc') as any;
        if (!rtc) { this.errorMessage = 'Direct transfer is not ready. Refresh both devices and try again.'; return; }
        try {
            await rtc.startBroadcast(files);
            (Alpine.store('app') as any).showUploadModal = false;
            this.createNewBatch();
        } catch (error) { this.errorMessage = (error as Error).message || 'Could not start direct transfer.'; }
    },

    async submit() {
        const batch = this.activeBatch;
        if (!batch || batch.files.length === 0 || this.isSubmitting) return;
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
