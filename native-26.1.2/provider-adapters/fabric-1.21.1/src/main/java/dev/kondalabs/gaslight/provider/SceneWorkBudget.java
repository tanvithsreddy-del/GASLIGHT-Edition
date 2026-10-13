package dev.kondalabs.gaslight.provider;

/** Two initial preparations and one replenished permit per thirty seconds.
 * The server owns this bucket across player sessions; failed attempts spend it. */
final class SceneWorkBudget {
    private static final long INTERVAL=30_000_000_000L;
    private int tokens=2;
    private long replenished;
    SceneWorkBudget(long now){replenished=now;}
    synchronized boolean consume(long now,long usedHeap,long maxHeap){
        if(now<replenished||usedHeap<0||maxHeap<=0)return false;
        long refill=(now-replenished)/INTERVAL;
        if(refill>0){tokens=(int)Math.min(2,tokens+refill);replenished+=refill*INTERVAL;}
        if(tokens==0||usedHeap>Math.min(700L*1024*1024,maxHeap*7/10))return false;
        tokens--;return true;
    }
}
