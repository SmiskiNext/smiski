import { apiRequest } from './client';
import { permissionEndpoints } from './endpoints';
import { permissionsFromBackend } from './mappers';

export interface RawMeetingPermissions {
  hasViewMeeting: boolean;
  hasEditMeeting: boolean;
}

export async function getMeetingPermission(projectKey: string): Promise<RawMeetingPermissions> {
  const payload = await apiRequest<unknown>(
    permissionEndpoints.projectMeetingPermissions(projectKey),
  );
  return permissionsFromBackend(payload);
}
