package framework.scuba.utils;

import java.util.Timer;
import java.util.TimerTask;

public class TaskTimer {
    Timer timer;

    public TaskTimer(int seconds) {
        System.out.format("Task scheduled.%n");
        timer = new Timer();
        timer.schedule(new RemindTask(), seconds*1000);
    }

    class RemindTask extends TimerTask {
        public void run() {
            System.out.format("Time's up! Exit current process.%n");
            timer.cancel(); //Terminate the timer thread
            System.exit(1);
        }
    }
}