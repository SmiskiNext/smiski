export type ChatMessage = {
    id: string;
    seqNum: number;
    roomId: string;
    senderId: string;
    senderName: string;
    content: string;
    type: 'TEXT' | 'SYSTEM';
    createdAt: string;
};
