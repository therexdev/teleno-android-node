#define _GNU_SOURCE
#include <sys/prctl.h>
#include <sys/file.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <signal.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>

/* Own the node's lifetime and lock. Never launch a second node after app death. */
static volatile sig_atomic_t stopping = 0;
static volatile sig_atomic_t node_pid = 0;
static void stop(int signo) { (void)signo; stopping = 1; if (node_pid > 0) kill(node_pid, SIGTERM); }
int main(int argc, char **argv) {
    if (argc < 4) return 64; /* lock, transient key path, executable, optional args... */
    umask(0077);
    pid_t app = getppid();
    struct sigaction action = {0}; action.sa_handler = stop; sigemptyset(&action.sa_mask);
    sigaction(SIGTERM, &action, NULL); sigaction(SIGINT, &action, NULL);
    if (prctl(PR_SET_PDEATHSIG, SIGTERM) || getppid() != app || stopping) return 70;
    int lock = open(argv[1], O_CREAT | O_RDWR | O_NOFOLLOW, 0600);
    if (lock < 0 || flock(lock, LOCK_EX | LOCK_NB)) { fputs("TELENO_ANDROID_NODE_ALREADY_RUNNING\n", stderr); return 73; }
    if (stopping) { close(lock); return 0; }
    pid_t supervisor = getpid();
    pid_t child = fork();
    if (child < 0) { close(lock); return 71; }
    if (child == 0) {
        signal(SIGTERM, SIG_DFL); signal(SIGINT, SIG_DFL);
        if (prctl(PR_SET_PDEATHSIG, SIGKILL) || getppid() != supervisor) _exit(70);
        execv(argv[3], argv + 3); _exit(127);
    }
    node_pid = child;
    if (stopping) kill(child, SIGTERM);
    int status = 0; pid_t waited;
    do { waited = waitpid(child, &status, 0); } while (waited < 0 && errno == EINTR);
    node_pid = 0;
    /* Only the caller's app-private transient signing file is supplied here. */
    unlink(argv[2]);
    close(lock);
    if (waited < 0) return 71;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    return 128 + WTERMSIG(status);
}
