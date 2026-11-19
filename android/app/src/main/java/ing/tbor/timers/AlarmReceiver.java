package ing.tbor.timers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Timer alarms, the notification's Stop / +1 min buttons, and re-arming after a reboot. */
public class AlarmReceiver extends BroadcastReceiver {
    static final String FIRE = "ing.tbor.timers.FIRE";
    static final String STOP = "ing.tbor.timers.STOP";
    static final String PLUS1 = "ing.tbor.timers.PLUS1";
    static final String REPOST = "ing.tbor.timers.REPOST";

    @Override public void onReceive(Context c, Intent in) {
        String a = in.getAction();
        int i = in.getIntExtra("i", -1);
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)) { Timers.reschedule(c); return; }
        if (i < 0 || i >= Timers.N) return;
        if (FIRE.equals(a)) Timers.finishIfDue(c, i);
        else if (STOP.equals(a)) Timers.stopAlarm(c, i);
        else if (PLUS1.equals(a)) Timers.addMinute(c, i, +1);
        else if (REPOST.equals(a)) Timers.repost(c, i);
        TimersActivity.refreshIfOpen();
    }
}
