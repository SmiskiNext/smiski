import type { ChildProcess } from 'node:child_process';
import { spawn } from 'node:child_process';

export interface ParallelTask {
    label: string;
    command: string;
    args: string[];
    cwd?: string;
    env?: NodeJS.ProcessEnv;
}

export async function runParallelWithKillAll(
    tasks: ParallelTask[],
): Promise<number> {
    if (tasks.length === 0) {
        return 0;
    }

    const children: ChildProcess[] = [];
    let firstFailureCode: number | null = null;
    let shuttingDown = false;

    const killAll = (signal: NodeJS.Signals = 'SIGTERM'): void => {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        for (const child of children) {
            if (child.exitCode === null && !child.killed) {
                child.kill(signal);
            }
        }
    };

    const forwardSignal = (signal: NodeJS.Signals): void => {
        killAll(signal);
    };

    process.on('SIGINT', forwardSignal);
    process.on('SIGTERM', forwardSignal);

    try {
        await new Promise<void>((resolvePromise) => {
            let remaining = tasks.length;

            for (const task of tasks) {
                const child = spawn(task.command, task.args, {
                    cwd: task.cwd ?? process.cwd(),
                    env: { ...process.env, ...task.env },
                    stdio: 'inherit',
                });
                children.push(child);

                child.on('exit', (code, signal) => {
                    const isFailure =
                        signal !== null || (code !== null && code !== 0);
                    if (isFailure) {
                        const exitCode = code ?? (signal !== null ? 128 : 1);
                        if (firstFailureCode === null) {
                            firstFailureCode = exitCode;
                        }
                        if (!shuttingDown) {
                            const reason =
                                signal !== null
                                    ? `signal ${signal}`
                                    : `code ${exitCode}`;
                            console.error(
                                `[${task.label}] exited (${reason}) — terminating remaining services`,
                            );
                            killAll();
                        }
                    } else {
                        console.log(
                            `[${task.label}] exited cleanly (code 0) — leaving remaining services running`,
                        );
                    }
                    remaining -= 1;
                    if (remaining === 0) {
                        resolvePromise();
                    }
                });

                child.on('error', (error) => {
                    console.error(
                        `[${task.label}] failed to start: ${error.message}`,
                    );
                    if (firstFailureCode === null) {
                        firstFailureCode = 1;
                    }
                    if (!shuttingDown) {
                        killAll();
                    }
                    remaining -= 1;
                    if (remaining === 0) {
                        resolvePromise();
                    }
                });
            }
        });
    } finally {
        process.off('SIGINT', forwardSignal);
        process.off('SIGTERM', forwardSignal);
    }

    return firstFailureCode ?? 0;
}
