TrScope : QWindow {
	var <>data;
	var <>minVal, <>maxVal;
	var <>pixelsPerSample;
	var <>on;
	var <maxSize;
	var lastTime;
	var <>frameRate;

	*new { arg name = "TrScope", bounds = Rect(83, 383, 500, 150), resizable = true, border = true, server,
		scroll = false, pixelsPerSample = 2, minVal = 0, maxVal = 1;

		^super.new(name, bounds, resizable, border, server, scroll).init(pixelsPerSample, minVal, maxVal);
	}

	init { arg iPixelsPerSample, iMinVal, iMaxVal;
		pixelsPerSample = iPixelsPerSample;
		minVal = iMinVal;
		maxVal = iMaxVal;
		data = Array.new;
		on = true;
		lastTime = thisThread.seconds;
		frameRate = 25;
		maxSize = (this.bounds.width / pixelsPerSample).asInteger;

		this.drawFunc = {
			maxSize = (this.bounds.width / pixelsPerSample).asInteger;
			if (data.size > 0) {
				Pen.moveTo(0@data[0].linlin(minVal, maxVal, this.bounds.height, 0));
				data.drop(1).do{|y, x|
					Pen.lineTo((x+1).linlin(0, maxSize, 0, this.bounds.width)@y.linlin(minVal, maxVal, this.bounds.height, 0));
				};
				Pen.stroke;
			}
		};

		this.view.keyDownAction = { arg doc, char, mod, unicode, keycode, key;
			if (unicode == 32) {
				on = on.not;
				if (on) {
					"TrTrace: running".postln;
				} {
					"TrTrace: paused".postln;
				};
			};

			if (keycode == 123) {
				pixelsPerSample = pixelsPerSample / 2.sqrt;
				("TrTrace: pixelsPerSample = " ++ pixelsPerSample).postln;
				this.refresh;
			};
			if (keycode == 124) {
				pixelsPerSample = pixelsPerSample * 2.sqrt;
				("TrTrace: pixelsPerSample = " ++ pixelsPerSample).postln;
				this.refresh;
			};
			if (keycode == 126) {
				minVal = minVal / 2.sqrt;
				maxVal = maxVal / 2.sqrt;
				("TrTrace: [min, max] = " ++ [minVal, maxVal]).postln;
				this.refresh;
			};
			if (keycode == 125) {
				minVal = minVal * 2.sqrt;
				maxVal = maxVal * 2.sqrt;
				("TrTrace: [min, max] = " ++ [minVal, maxVal]).postln;
				this.refresh;
			};
		};

		^this
	}

	addData { arg d;
		if (on) {
			var now = thisThread.seconds;

			if (data.size >= maxSize) {
				data = data.keep(maxSize - d.size);
			};
			data = d ++ data;
			if ((now - lastTime) > frameRate.reciprocal) {
				lastTime = now;
				{ this.refresh }.defer;
			}
		}
	}

	maxSize_ { arg size;
		maxSize = size;
		if (data.size > maxSize) {
			data = data.keep(maxSize);
		}
	}

}
