/**
 * Reports whether the process can run an interactive prompt.
 *
 * Both streams are checked because a prompt needs to read keystrokes and to
 * redraw its own output. When either is a pipe rather than a terminal, consola's
 * prompt fails inside libuv with `uv_tty_init returned EINVAL` — an unhandled
 * error and a stack trace, which reads as a broken command rather than as the
 * unsupported environment it actually is.
 */
export function isInteractive(): boolean {
    return process.stdin.isTTY === true && process.stdout.isTTY === true;
}
