// if alive was not set within interval, callback is called

TrWatchDog {
	var <>interval;
	var <>callback;
	var <>alive;
	var task;

	*new { arg interval, callback;
		^super.new.init(interval, callback);
	}

	init { arg argInterval, argCallback;
		interval = argInterval;
		callback = argCallback;
		task = Task {
			loop {
				alive = false;
				interval.wait;
				if (alive.not) {
					callback.value(this);
				}
			}
		};
		this.start;
	}

	start {
		task.start;
	}

	stop {
		task.stop;
	}
}
