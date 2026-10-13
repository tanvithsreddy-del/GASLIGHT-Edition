package dev.kondalabs.gaslight.provider;
import java.util.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class CameraFenceTest {
    private FrameClip clip(){return new FrameClip(0,0,0,new Matrix4f().perspective((float)Math.toRadians(90),1,.05f,128).get(new float[16]));}
    @Test void wholeBoxAndGrazingGeometry(){
        var c=clip();assertFalse(c.excludes(-1,-1,-10,1,1,-8));assertTrue(c.excludes(-1,-1,8,1,1,10));
        assertFalse(c.excludes(-100,-100,-10,100,100,10));assertFalse(c.excludes(7.9,-1,-10,10,1,-8));
        assertThrows(IllegalArgumentException.class,()->c.excludes(1,0,0,-1,1,1));
    }
    @Test void immutableAndInvalidMatrices(){
        float[] m=new Matrix4f().perspective(1,1,.05f,128).get(new float[16]);var c=new FrameClip(0,0,0,m);m[0]=Float.NaN;assertTrue(Float.isFinite(c.matrix()[0]));
        assertThrows(IllegalArgumentException.class,()->new FrameClip(0,0,0,m));assertThrows(IllegalArgumentException.class,()->new FrameClip(0,0,0,new float[16]));
    }
    @Test void sessionGenerationDimensionSequenceAndDistance(){
        var s=new CameraTelemetry.Session(4,"minecraft:overworld");long now=1_000_000_001;
        assertTrue(s.accept(new CameraTelemetry.Observation(s.nonce,4,1,s.dimension,clip(),true),0,0,0,now));
        assertFalse(s.accept(new CameraTelemetry.Observation(s.nonce,4,1,s.dimension,clip(),true),0,0,0,now+1));
        assertFalse(s.accept(new CameraTelemetry.Observation(UUID.randomUUID(),4,2,s.dimension,clip(),true),0,0,0,now+2));
        assertFalse(s.accept(new CameraTelemetry.Observation(s.nonce,3,2,s.dimension,clip(),true),0,0,0,now+3));
        assertFalse(s.accept(new CameraTelemetry.Observation(s.nonce,4,2,"minecraft:the_nether",clip(),true),0,0,0,now+4));
        assertFalse(s.accept(new CameraTelemetry.Observation(s.nonce,4,2,s.dimension,clip(),true),100,0,0,now+5));
        assertFalse(s.accept(new CameraTelemetry.Observation(s.nonce,4,2,s.dimension,clip(),false),0,0,0,now+6));
    }
    @Test void observationFloodCannotBecomeAuthority(){
        var s=new CameraTelemetry.Session(1,"minecraft:overworld");int count=0;
        for(int i=0;i<100;i++)if(s.accept(new CameraTelemetry.Observation(s.nonce,1,i,s.dimension,clip(),true),0,0,0,2_000_000_000L))count++;
        assertEquals(12,count);assertNull(s.latest);
    }
    @Test void realPacketCodecRejectsTruncationAndInvalidMatrix(){
        var packet=new CameraTelemetry.Observation(UUID.randomUUID(),7,13,"minecraft:overworld",clip(),true);
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try{
            CameraTelemetry.Observation.CODEC.encode(buffer,packet);int size=buffer.readableBytes();System.out.println("CAMERA_OBSERVATION_PAYLOAD_BYTES="+size);
            var decoded=CameraTelemetry.Observation.CODEC.decode(buffer);assertEquals(packet.session(),decoded.session());assertEquals(13,decoded.frame());assertArrayEquals(packet.clip().matrix(),decoded.clip().matrix());
            buffer.clear();CameraTelemetry.Observation.CODEC.encode(buffer,packet);buffer.writerIndex(size-1);
            assertThrows(IndexOutOfBoundsException.class,()->CameraTelemetry.Observation.CODEC.decode(buffer));
            buffer.clear();CameraTelemetry.Observation.CODEC.encode(buffer,packet);
            // Final 64-byte matrix is followed by one boolean. Poison its first value.
            buffer.setFloat(size-65,Float.NaN);
            assertThrows(IllegalArgumentException.class,()->CameraTelemetry.Observation.CODEC.decode(buffer));
        }finally{buffer.release();}
    }
}
