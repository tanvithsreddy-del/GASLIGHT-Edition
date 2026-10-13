package dev.kondalabs.gaslight.provider;

import java.util.UUID;

/** One connection's whole-scene selection. A failed selection cannot roll back
 * into an already observed view; callers close the scene and retain the intent. */
public final class GenerationFence {
    public enum State { PREPARING, READY, SELECTING, COMMITTED, RETIRED, FAILED }
    private final UUID session;
    private final long generation, deadline;
    private State state = State.PREPARING;
    private long frame = -1;
    public GenerationFence(UUID session, long generation, long deadline) {
        if (session == null || generation <= 0 || deadline <= 0) throw new IllegalArgumentException("generation identity");
        this.session = session; this.generation = generation; this.deadline = deadline;
    }
    public synchronized State state() { return state; }
    public synchronized long frame() { return frame; }
    public UUID session() { return session; }
    public long generation() { return generation; }
    private boolean live(UUID nonce, long epoch, long now) {
        return session.equals(nonce) && generation == epoch && now < deadline;
    }
    public synchronized boolean ready(UUID nonce, long epoch, boolean data, boolean lighting, boolean meshes, long now) {
        if (!live(nonce, epoch, now) || state != State.PREPARING || !data || !lighting || !meshes) return false;
        state = State.READY; return true;
    }
    public synchronized boolean select(UUID nonce, long epoch, long renderedFrame, boolean wholeAffectedViewHidden, boolean supported, long now) {
        if (!live(nonce, epoch, now) || state != State.READY || renderedFrame < 0 || !wholeAffectedViewHidden || !supported) return false;
        frame = renderedFrame; state = State.SELECTING; return true;
    }
    public synchronized boolean invalidateReady(UUID nonce,long epoch,long now){
        if(!live(nonce,epoch,now)||state!=State.READY)return false;
        state=State.PREPARING;return true;
    }
    public synchronized boolean commit(UUID nonce, long epoch, long selectedFrame, long now) {
        if (!live(nonce, epoch, now) || state != State.SELECTING || selectedFrame != frame) return false;
        state = State.COMMITTED; return true;
    }
    public synchronized boolean retire(UUID nonce, long epoch, boolean unoccupied, boolean hidden) {
        if (!session.equals(nonce) || generation != epoch || state != State.COMMITTED || !unoccupied || !hidden) return false;
        state = State.RETIRED; return true;
    }
    public synchronized void fail() { if (state != State.RETIRED) state = State.FAILED; }
}
