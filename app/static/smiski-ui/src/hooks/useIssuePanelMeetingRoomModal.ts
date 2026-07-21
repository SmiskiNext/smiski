/**
 * Opens a meeting room in a large Forge platform modal so Start/Join actions
 * launched from an Issue Panel are never constrained by that panel's iframe.
 * Vite development keeps the existing project-page surface handoff because
 * the Forge modal bridge is unavailable outside Jira.
 */
import { Modal as ForgeModal } from '@forge/bridge';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigateToMeetingRoom } from './useNavigateToMeetingRoom';
import {
  MEETING_ROOM_MODAL_KIND,
  type MeetingRoomModalContext,
} from '../utils/issuePanelModalContext';

export function useIssuePanelMeetingRoomModal(onDevNavigate?: () => void) {
  const queryClient = useQueryClient();
  const navigateToDevMeetingRoom = useNavigateToMeetingRoom(onDevNavigate);

  const open = (projectKey: string, meetingId: string) => {
    if (import.meta.env.DEV) {
      navigateToDevMeetingRoom(projectKey, meetingId);
      return;
    }

    const context: MeetingRoomModalContext = {
      kind: MEETING_ROOM_MODAL_KIND,
      meetingId,
    };
    new ForgeModal({
      context,
      size: 'large',
      onClose: () => queryClient.invalidateQueries({ queryKey: ['meetings'] }),
    }).open();
  };

  return open;
}
