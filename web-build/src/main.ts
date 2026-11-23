// SPDX-FileCopyrightText: 2026 2026 defname
//
// SPDX-License-Identifier: GPL-3.0-or-later

import Alpine from 'alpinejs';
import { AppStore } from './stores/app';
import { SidebarStore } from './stores/sidebar';
import { FileManager } from './components/fileManager';
import { Dropzone } from './components/dropzone';

// Lightweight in-page diagnostics. Kept in memory and exportable by the user.
const debugEntries: Array<{ time: string; level: string; message: string; details?: unknown }> = [];
const debug = {
    log(level: string, message: string, details?: unknown) {
        const entry = { time: new Date().toISOString(), level, message,
            details: details === undefined ? undefined : (() => {
                try { return JSON.parse(JSON.stringify(details, (_key, value) =>
                    value instanceof File ? { name: value.name, size: value.size, type: value.type, lastModified: value.lastModified } : value
                )); } catch { return String(details); }
            })() };
        debugEntries.push(entry);
        if (debugEntries.length > 500) debugEntries.shift();
        const method = level === 'error' ? 'error' : level === 'warn' ? 'warn' : 'log';
        console[method]('[LocalShare]', message, details ?? '');
        window.dispatchEvent(new CustomEvent('localshare:debug', { detail: entry }));
    },
    getEntries() { return [...debugEntries]; },
    download() {
        const blob = new Blob([JSON.stringify({ exportedAt: new Date().toISOString(), userAgent: navigator.userAgent, url: location.href, entries: debugEntries }, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url; anchor.download = 'localshare-diagnostics.json'; anchor.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    },
    clear() { debugEntries.splice(0, debugEntries.length); this.log('info', 'Diagnostic log cleared'); }
};
(window as any).LocalShareDebug = debug;
debug.log('info', 'Web app script loaded', { readyState: document.readyState, userAgent: navigator.userAgent });
window.addEventListener('error', event => debug.log('error', 'Window error', { message: event.message, filename: event.filename, line: event.lineno, column: event.colno }));
window.addEventListener('unhandledrejection', event => debug.log('error', 'Unhandled promise rejection', { reason: String(event.reason), stack: event.reason?.stack }));
document.addEventListener('change', event => {
    const target = event.target as HTMLInputElement;
    if (target?.type === 'file') debug.log('info', 'Captured file input change (document)', { files: Array.from(target.files || []).map(file => ({ name: file.name, size: file.size, type: file.type })) });
}, true);

// Initialize SSE as a global property so it's available before stores init
const sse = new EventSource(`/${window.token}/events`);
let initialClientId: string | null = null;
sse.addEventListener('init', (event: MessageEvent) => {
    try { initialClientId = (JSON.parse(event.data) as { clientId: string }).clientId; } catch { /* ignore malformed init */ }
});

document.addEventListener('alpine:init', () => {
    // 1. Register Global Stores
    Alpine.store('sse', sse);
    Alpine.store('app', AppStore);
    Alpine.store('sidebar', SidebarStore);
    Alpine.store('clientId', initialClientId);

    // 2. Register Components
    Alpine.data('fileManager', FileManager);
    Alpine.data('dropzone', Dropzone);

    // 3. Global Binds (Extracted from template.html)
    Alpine.bind('ThemeToggle', () => ({
        '@click'() {
            (this as any).darkMode = !(this as any).darkMode;
            localStorage.setItem('dark', String((this as any).darkMode));
        },
        'class': 'ls-btn-icon'
    }));

    Alpine.bind('UploadButton', () => ({
        '@click'() {
            (Alpine.store('app') as any).showUploadModal = true;
        },
        'class': 'ls-btn-icon'
    }));

    Alpine.bind('SidebarToggle', () => ({
        '@click'() {
            (Alpine.store('sidebar') as any).toggle();
        },
        'class': 'ls-btn-icon relative'
    }));
});

// Start Alpine
(window as any).Alpine = Alpine;
Alpine.start();
