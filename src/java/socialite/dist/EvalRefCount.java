package socialite.dist;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class EvalRefCount {
    public static final Log L = LogFactory.getLog(EvalRefCount.class);

    public interface IdleCallback {
        public void call(int id, int idleTimestamp);
    }

    static volatile EvalRefCount theInst;

    public static EvalRefCount getInst(IdleCallback callback) {
        theInst = new EvalRefCount(callback);
        return theInst;
    }

    public static EvalRefCount getInst() {
//        if (theInst == null) {
//            throw new SociaLiteException("EvalRefCount is not created yet");
//        }
        while (theInst == null) ;//fix uninitiated bug
        return theInst;
    }

    ConcurrentHashMap<Integer, AtomicInteger> ready;
    ConcurrentHashMap<Integer, AtomicInteger> counterMap;
    ConcurrentHashMap<Integer, AtomicInteger> idleTimestampMap;
    IdleCallback callback;

    public EvalRefCount(IdleCallback _callback) {
        this(_callback, 32);
    }

    public EvalRefCount(IdleCallback _callback, int concurrencyLevel) {
        ready = new ConcurrentHashMap<>(256, 0.75f, concurrencyLevel);
        counterMap = new ConcurrentHashMap<>(256, 0.75f, concurrencyLevel);
        idleTimestampMap = new ConcurrentHashMap<>(256, 0.75f, concurrencyLevel);
        callback = _callback;
    }

    public void setReady(int id) {
        synchronized (ready) {
        }

        AtomicInteger val = new AtomicInteger(1);
        AtomicInteger prev = ready.putIfAbsent(id, val);
        if (prev != null) {
            val = prev;
        }
        val.incrementAndGet();

        synchronized (val) {
            val.notifyAll();
        }
    }

    public void waitUntilReady(int id) throws InterruptedException {
        synchronized (ready) {
        }

        AtomicInteger val = new AtomicInteger(0);
        AtomicInteger prev = ready.putIfAbsent(id, val);
        if (prev != null) {
            val = prev;
        }
        synchronized (val) {
            while (true) {
                if (val.get() > 0) {
                    break;
                }
                val.wait(1);
            }
        }
    }

    public void incBy(int id, int by) {
//        L.info("call incBy "+id+", "+by);
        if (!counterMap.containsKey(id)) {
            counterMap.putIfAbsent(id, new AtomicInteger(0));
        }
        AtomicInteger counter = counterMap.get(id);
        counter.addAndGet(by);
    }

    public void inc(int id) {
//        L.info("call inc "+id);
        if (!counterMap.containsKey(id)) {
            counterMap.putIfAbsent(id, new AtomicInteger(0));
        }
        AtomicInteger counter = counterMap.get(id);
        counter.incrementAndGet();
    }

    public void decBy(int id, int by) {
//        L.info("call decBy "+id+", "+by);
        AtomicInteger cnt = counterMap.get(id);
        if (cnt == null) {
            L.info("Epoch[" + id + "] is not registered");
            return;
        }
        if (cnt.addAndGet(-by) == 0) {
            if (!idleTimestampMap.containsKey(id)) {
                idleTimestampMap.putIfAbsent(id, new AtomicInteger(0));
            }
            AtomicInteger ts = idleTimestampMap.get(id);
            callback.call(id, ts.incrementAndGet());
        }
    }

    public void dec(int id) {
//        L.info("call dec "+id);
        AtomicInteger cnt = counterMap.get(id);
        if (cnt == null) {
            L.info("Epoch[" + id + "] is not registered");
            return;
        }
        int x = cnt.decrementAndGet();
        if (x == 0) {
            if (!idleTimestampMap.containsKey(id)) {
                idleTimestampMap.putIfAbsent(id, new AtomicInteger(0));
            }
            AtomicInteger ts = idleTimestampMap.get(id);
            callback.call(id, ts.incrementAndGet());
        }
    }

    public boolean stillIdle(int id, int ts) {
        AtomicInteger counter = counterMap.get(id);
        AtomicInteger timestamp = idleTimestampMap.get(id);
        if (counter == null || timestamp == null) {
            return false;
        }
        return counter.get() == 0 && timestamp.get() == ts;
    }

    public void discard(int id) {
        callback.call(id, -1);
        clear(id);
    }

    public void clear(int id) {
        ready.remove(id);
        counterMap.remove(id);
        idleTimestampMap.remove(id);
    }
}
