package dev.kondalabs.gaslight;
import java.util.function.LongSupplier;
/** Wall-time token accounting: no accelerated tick loop can increase bandwidth. */
public final class RemoteWorldBudget {
    public static final long BYTES_PER_SECOND=262_144, BURST_BYTES=65_536;
    public static final long CPU_PER_SECOND=100_000_000, CAPTURE_NANOS=40_000_000;
    private final LongSupplier clock;private final long rate,burst;private long last;private double bytes,cpu=CAPTURE_NANOS;
    public RemoteWorldBudget(LongSupplier clock){this(clock,BYTES_PER_SECOND,BURST_BYTES);}
    public RemoteWorldBudget(LongSupplier clock,long rate,long burst){if(rate<1||burst<1)throw new IllegalArgumentException("token budget");this.clock=clock;this.rate=rate;this.burst=burst;bytes=burst;last=clock.getAsLong();}
    private void refill(){long now=clock.getAsLong();if(now<last){bytes=0;cpu=0;last=now;return;}double seconds=Math.min(10,(now-last)/1e9);last=now;bytes=Math.min(burst,bytes+seconds*rate);cpu=Math.min(CAPTURE_NANOS,cpu+seconds*CPU_PER_SECOND);}
    public boolean send(int count){refill();if(count<0||count>bytes)return false;bytes-=count;return true;}
    public boolean canCapture(){refill();return cpu>=CAPTURE_NANOS;}
    public void captureCost(long nanos){refill();cpu-=Math.max(0,nanos);}
}
