package dev.kondalabs.gaslight.provider;
/** A bounded token bucket for the encoded wire payload, independent of chunks. */
final class ByteBudget {
    private final long capacity,perSecond;
    private double tokens;
    private long previous;
    ByteBudget(long capacity,long perSecond,long now){if(capacity<1||perSecond<1)throw new IllegalArgumentException("byte budget");this.capacity=capacity;this.perSecond=perSecond;tokens=capacity;previous=now;}
    synchronized boolean spend(long bytes,long now){
        if(bytes<0||now<previous)return false;
        tokens=Math.min(capacity,tokens+((now-previous)/1e9)*perSecond);previous=now;
        if(bytes>tokens)return false;tokens-=bytes;return true;
    }
}
