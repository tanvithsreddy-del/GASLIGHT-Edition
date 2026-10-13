package dev.kondalabs.gaslight.provider;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
/** Separate real process, deliberately halted without graceful connection close. */
public final class SqliteCrashProbe {
    public static void main(String[] args)throws Exception{
        Path path=Path.of(args[1]);String mode=args[0];
        try(var store=new SqliteIntentStore(path)){
            if(!store.begin(List.of(SqliteIntentStoreTest.intent(1))))throw new AssertionError(store.diagnostic());
            if(mode.equals("begin")){System.out.println("DURABLE_BEGIN_ACKNOWLEDGED");System.out.flush();Runtime.getRuntime().halt(71);}
            if(mode.equals("settle")){
                if(!store.settle(SqliteIntentStoreTest.intent(1).owner(),1))throw new AssertionError(store.diagnostic());
                System.out.println("DURABLE_TOMBSTONE_ACKNOWLEDGED");System.out.flush();Runtime.getRuntime().halt(72);
            }
        }
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+path);var s=c.createStatement()){
            s.execute("PRAGMA journal_mode=PERSIST");s.execute("PRAGMA synchronous=FULL");s.execute("BEGIN IMMEDIATE");
            s.executeUpdate("UPDATE intents SET payload=x'00'");
            System.out.println("INTERRUPTED_UNCOMMITTED_PAYLOAD_UPDATE");System.out.flush();Runtime.getRuntime().halt(73);
        }
    }
}
