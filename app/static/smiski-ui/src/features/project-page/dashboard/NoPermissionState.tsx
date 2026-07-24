import { Icon } from '../../../components/ui';

export function NoPermissionState() {
    return (
        <div className='mx-auto mt-16 max-w-lg border bg-[var(--surface)] p-6 text-center shadow-sm'>
            <div className='mx-auto mb-4 flex size-10 items-center justify-center border bg-[var(--surface-soft)] text-[var(--text-muted)]'>
                <Icon name='alert' size={18} />
            </div>
            <h2 className='text-base font-semibold text-[var(--text)]'>
                Không có quyền truy cập Meetings
            </h2>
            <p className='mt-2 text-sm leading-6 text-[var(--text-muted)]'>
                Bạn không có quyền truy cập Meeting trong project này. Vui lòng
                liên hệ Jira Administrator hoặc Project Administrator.
            </p>
        </div>
    );
}
