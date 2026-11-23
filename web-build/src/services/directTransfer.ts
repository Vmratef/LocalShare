import { apiActionRequest } from './api';
import { WebActionWebRtcSignal } from '../types';

export interface DirectOffer {
    sessionId: string;
    from: string;
    name: string;
    size: number;
    mimeType: string;
}
export interface DirectTransferStatus {
    sessionId: string;
    name: string;
    direction: 'sending' | 'receiving';
    status: string;
    progress: number;
    size: number;
    error?: string;
}
type Signal = {
    kind: 'discover' | 'accept' | 'reject' | 'offer' | 'answer' | 'candidate' | 'cancel';
    name?: string; size?: number; mimeType?: string;
    description?: RTCSessionDescriptionInit;
    candidate?: RTCIceCandidateInit;
};
type PeerState = {
    pc: RTCPeerConnection; channel?: RTCDataChannel; file?: File; remoteIp: string;
    direction: 'sending' | 'receiving'; received: number; chunks: BlobPart[];
    writable?: any; writeChain: Promise<void>; metadata?: { name: string; size: number; mimeType: string };
};
const CHUNK_SIZE = 64 * 1024;
const BUFFERED_LIMIT = 1024 * 1024;
const MAX_IN_MEMORY_FALLBACK = 512 * 1024 * 1024;

export class DirectTransferManager {
    private peers = new Map<string, PeerState>();
    private outgoing = new Map<string, File>();
    private offers = new Map<string, DirectOffer>();
    private pendingCandidates = new Map<string, RTCIceCandidateInit[]>();
    private sse: EventSource;
    private clientId: string;
    private onOffer: (offer: DirectOffer) => void;
    private onStatus: (status: DirectTransferStatus) => void;
    private signalHandler: (event: MessageEvent) => void;

    constructor(sse: EventSource, clientId: string, callbacks: {
        onOffer: (offer: DirectOffer) => void;
        onStatus: (status: DirectTransferStatus) => void;
    }) {
        this.sse = sse; this.clientId = clientId;
        this.onOffer = callbacks.onOffer; this.onStatus = callbacks.onStatus;
        this.signalHandler = event => {
            try {
                const data = JSON.parse(event.data) as { sessionId: string; from: string; payload: string };
                void this.handleSignal(data.sessionId, data.from, JSON.parse(data.payload) as Signal);
            } catch (error) { console.warn('Ignoring malformed WebRTC signaling event', error); }
        };
        this.sse.addEventListener('web_rtc_signal', this.signalHandler);
    }

    dispose() {
        this.sse.removeEventListener('web_rtc_signal', this.signalHandler);
        for (const id of this.peers.keys()) this.closePeer(id);
        this.outgoing.clear(); this.offers.clear();
    }

    async startBroadcast(files: File[]) {
        for (const file of files) {
            const id = this.newId();
            this.outgoing.set(id, file);
            this.report(id, file.name, 'sending', 'Waiting for a receiver', 0, file.size);
            await this.sendSignal(id, { kind: 'discover', name: file.name, size: file.size,
                mimeType: file.type || 'application/octet-stream' });
        }
    }

    async accept(id: string) {
        const offer = this.offers.get(id);
        if (!offer) throw new Error('This transfer request is no longer available.');
        let writable: any;
        const picker = (window as any).showSaveFilePicker;
        if (typeof picker === 'function') {
            const handle = await picker({ suggestedName: offer.name });
            writable = await handle.createWritable();
        } else if (offer.size > MAX_IN_MEMORY_FALLBACK) {
            throw new Error('This browser cannot stream a file this large to disk. Use a browser with File System Access support. No file data was sent through Android.');
        }
        this.offers.delete(id);
        this.peers.set(id, { pc: this.createPeerConnection(id, offer.from), remoteIp: offer.from,
            direction: 'receiving', received: 0, chunks: [], writable, writeChain: Promise.resolve() });
        this.report(id, offer.name, 'receiving', 'Waiting for direct connection', 0, offer.size);
        await this.sendSignal(id, { kind: 'accept' }, offer.from);
    }

    async reject(id: string) {
        const offer = this.offers.get(id);
        if (!offer) return;
        this.offers.delete(id);
        await this.sendSignal(id, { kind: 'reject' }, offer.from);
    }

    async cancel(id: string) {
        const peer = this.peers.get(id);
        const offer = this.offers.get(id);
        const file = this.outgoing.get(id);
        await this.sendSignal(id, { kind: 'cancel' }, peer?.remoteIp || offer?.from);
        if (peer?.writable) { try { await peer.writable.abort(); } catch { /* ignore */ } }
        this.closePeer(id); this.offers.delete(id); this.outgoing.delete(id);
        this.report(id, file?.name || peer?.metadata?.name || 'File', peer?.direction || 'sending',
            'Canceled', 0, file?.size || peer?.metadata?.size || 0);
    }

    private async handleSignal(id: string, from: string, signal: Signal) {
        if (from === this.clientId) return;
        switch (signal.kind) {
            case 'discover':
                if (!signal.name || typeof signal.size !== 'number') return;
                if (!this.offers.has(id)) {
                    const offer = { sessionId: id, from, name: signal.name, size: signal.size,
                        mimeType: signal.mimeType || 'application/octet-stream' };
                    this.offers.set(id, offer); this.onOffer(offer);
                }
                break;
            case 'accept': {
                const file = this.outgoing.get(id);
                if (file && !this.peers.has(id)) await this.createSenderPeer(id, from, file);
                break;
            }
            case 'reject': {
                const file = this.outgoing.get(id);
                if (file) this.report(id, file.name, 'sending', 'Receiver declined', 0, file.size);
                break;
            }
            case 'offer': await this.createReceiverPeer(id, from, signal); break;
            case 'answer': {
                const peer = this.peers.get(id);
                if (peer && signal.description) {
                    await peer.pc.setRemoteDescription(signal.description);
                    await this.flushCandidates(id);
                }
                break;
            }
            case 'candidate': {
                if (!signal.candidate) return;
                const peer = this.peers.get(id);
                if (!peer || !peer.pc.remoteDescription) {
                    const list = this.pendingCandidates.get(id) || [];
                    list.push(signal.candidate); this.pendingCandidates.set(id, list);
                } else {
                    try { await peer.pc.addIceCandidate(signal.candidate); } catch (error) { console.warn(error); }
                }
                break;
            }
            case 'cancel': {
                const peer = this.peers.get(id);
                const file = this.outgoing.get(id);
                if (peer?.writable) { try { await peer.writable.abort(); } catch { /* ignore */ } }
                this.closePeer(id); this.offers.delete(id); this.outgoing.delete(id);
                this.report(id, file?.name || peer?.metadata?.name || 'File', peer?.direction || 'receiving',
                    'Canceled by other device', peer?.received || 0, file?.size || peer?.metadata?.size || 0);
                break;
            }
        }
    }

    private async createSenderPeer(id: string, remoteIp: string, file: File) {
        const pc = this.createPeerConnection(id, remoteIp);
        const peer: PeerState = { pc, file, remoteIp, direction: 'sending', received: 0, chunks: [], writeChain: Promise.resolve() };
        this.peers.set(id, peer);
        const channel = pc.createDataChannel('localshare-file', { ordered: true });
        peer.channel = channel; channel.bufferedAmountLowThreshold = BUFFERED_LIMIT / 2;
        channel.onopen = () => { void this.sendFile(id, peer); };
        channel.onerror = () => this.report(id, file.name, 'sending', 'Data channel error', 0, file.size);
        channel.onmessage = event => {
            if (typeof event.data !== 'string') return;
            try {
                const message = JSON.parse(event.data);
                if (message.type === 'ack') {
                    this.report(id, file.name, 'sending', 'Sent directly', file.size, file.size);
                    this.outgoing.delete(id);
                    this.closePeer(id);
                }
            } catch { /* ignore unrelated channel messages */ }
        };
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        await this.sendSignal(id, { kind: 'offer', description: pc.localDescription! }, remoteIp);
        this.report(id, file.name, 'sending', 'Connecting directly', 0, file.size);
    }

    private async createReceiverPeer(id: string, remoteIp: string, signal: Signal) {
        const peer = this.peers.get(id);
        if (!peer || peer.direction !== 'receiving' || !signal.description) return;
        peer.pc.ondatachannel = event => {
            peer.channel = event.channel; event.channel.binaryType = 'arraybuffer';
            event.channel.onmessage = message => {
                peer.writeChain = peer.writeChain.then(() => this.receiveMessage(id, peer, message.data))
                    .catch(error => this.report(id, peer.metadata?.name || 'File', 'receiving', 'Write failed', peer.received, peer.metadata?.size || 0, (error as Error).message));
            };
        };
        await peer.pc.setRemoteDescription(signal.description);
        const answer = await peer.pc.createAnswer();
        await peer.pc.setLocalDescription(answer);
        await this.sendSignal(id, { kind: 'answer', description: peer.pc.localDescription! }, remoteIp);
        await this.flushCandidates(id);
    }

    private createPeerConnection(id: string, remoteIp: string) {
        const pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] });
        pc.onicecandidate = event => {
            if (event.candidate) void this.sendSignal(id,
                { kind: 'candidate', candidate: event.candidate.toJSON() }, remoteIp);
        };
        pc.onconnectionstatechange = () => {
            if (pc.connectionState === 'failed') {
                const file = this.outgoing.get(id); const peer = this.peers.get(id);
                this.report(id, file?.name || peer?.metadata?.name || 'File', peer?.direction || 'sending',
                    'Direct connection failed. No Android relay fallback was used.',
                    peer?.received || 0, file?.size || peer?.metadata?.size || 0);
                this.closePeer(id);
            }
        };
        return pc;
    }

    private async sendFile(id: string, peer: PeerState) {
        const file = peer.file!; const channel = peer.channel!;
        try {
            channel.send(JSON.stringify({ type: 'start', name: file.name, size: file.size,
                mimeType: file.type || 'application/octet-stream' }));
            let offset = 0;
            while (offset < file.size) {
                if (channel.readyState !== 'open') throw new Error('Direct connection closed.');
                if (channel.bufferedAmount > BUFFERED_LIMIT) {
                    await new Promise<void>(resolve => channel.addEventListener('bufferedamountlow', () => resolve(), { once: true }));
                }
                const end = Math.min(offset + CHUNK_SIZE, file.size);
                channel.send(await this.readSlice(file.slice(offset, end)));
                offset = end;
                this.report(id, file.name, 'sending', 'Sending directly', offset, file.size);
            }
            channel.send(JSON.stringify({ type: 'finish' }));
            this.report(id, file.name, 'sending', 'Waiting for receiver confirmation', file.size, file.size);
        } catch (error) {
            this.report(id, file.name, 'sending', 'Transfer failed', 0, file.size, (error as Error).message);
        }
    }

    private readSlice(blob: Blob): Promise<ArrayBuffer> {
        if (typeof (blob as any).arrayBuffer === 'function') return (blob as any).arrayBuffer();
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result as ArrayBuffer);
            reader.onerror = () => reject(reader.error || new Error('Could not read file chunk.'));
            reader.readAsArrayBuffer(blob);
        });
    }

    private async receiveMessage(id: string, peer: PeerState, data: any) {
        if (typeof data === 'string') {
            let message: any;
            try { message = JSON.parse(data); } catch { return; }
            if (message.type === 'start') {
                peer.metadata = { name: message.name, size: message.size, mimeType: message.mimeType };
                this.report(id, message.name, 'receiving', 'Receiving directly', 0, message.size);
            } else if (message.type === 'finish') {
                if (!peer.metadata) return;
                if (peer.received !== peer.metadata.size) {
                    if (peer.writable) await peer.writable.abort();
                    this.report(id, peer.metadata.name, 'receiving', 'Transfer incomplete: size mismatch', peer.received, peer.metadata.size);
                    this.closePeer(id);
                    return;
                }
                if (peer.writable) await peer.writable.close();
                else {
                    const blob = new Blob(peer.chunks, { type: peer.metadata.mimeType });
                    const url = URL.createObjectURL(blob);
                    const anchor = document.createElement('a');
                    anchor.href = url; anchor.download = peer.metadata.name; anchor.click();
                    setTimeout(() => URL.revokeObjectURL(url), 60_000);
                }
                try { peer.channel?.send(JSON.stringify({ type: 'ack' })); } catch { /* sender may already be gone */ }
                this.report(id, peer.metadata.name, 'receiving', 'Received directly',
                    peer.metadata.size, peer.metadata.size);
                setTimeout(() => this.closePeer(id), 1000);
            } else if (message.type === 'cancel') {
                if (peer.writable) await peer.writable.abort();
                this.report(id, peer.metadata?.name || 'File', 'receiving', 'Canceled by sender',
                    peer.received, peer.metadata?.size || 0);
                this.closePeer(id);
            }
            return;
        }
        if (!peer.metadata) return;
        if (peer.writable) await peer.writable.write(data);
        else peer.chunks.push(data);
        peer.received += data.byteLength;
        this.report(id, peer.metadata.name, 'receiving', 'Receiving directly',
            peer.received, peer.metadata.size);
    }

    private async flushCandidates(id: string) {
        const peer = this.peers.get(id); const list = this.pendingCandidates.get(id) || [];
        if (!peer || !peer.pc.remoteDescription) return;
        this.pendingCandidates.delete(id);
        for (const candidate of list) {
            try { await peer.pc.addIceCandidate(candidate); } catch (error) { console.warn(error); }
        }
    }

    private async sendSignal(id: string, signal: Signal, targetIp?: string) {
        const action: WebActionWebRtcSignal = {
            type: 'web_rtc_signal', sessionId: id, payload: JSON.stringify(signal), targetIp
        };
        await apiActionRequest(action);
    }

    private closePeer(id: string) {
        const peer = this.peers.get(id);
        if (peer) {
            try { peer.channel?.close(); } catch { /* ignore */ }
            try { peer.pc.close(); } catch { /* ignore */ }
            this.peers.delete(id);
        }
        this.pendingCandidates.delete(id);
    }

    private report(id: string, name: string, direction: 'sending' | 'receiving',
                   status: string, progress: number, size: number, error?: string) {
        this.onStatus({ sessionId: id, name, direction, status, progress, size, error });
    }

    private newId() {
        return (crypto as any).randomUUID?.() ||
            ('ls-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2));
    }
}