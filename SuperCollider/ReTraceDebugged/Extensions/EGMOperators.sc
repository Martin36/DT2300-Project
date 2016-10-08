EGM_nop {
	value { arg inval;
		^inval;
	}
}

EGM_change {
	var last;

	*new {
		^super.new.reset;
	}

	reset {
		last = nil;
	}

	value { arg inval;
		if (last == inval) {
			^nil;
		} {
			last = inval;
			^inval;
		};
	}
}

// detects zero input

EGM_isZero {
	var count;
	var delay;
	var name;

	*new { arg delay = 0, name = nil;
		^super.new.init(delay, name);
	}

	init { arg idelay, iname;
		count = 0;
		delay = idelay;
		name = iname;
	}

	value { arg inval;
		if (inval.sum == 0) {
			if (name.notNil && (count <= 0)) {
				name.post;
				": zero detected".postln;
			};
			count = delay;
			^0;
		} {
			if (count > 0) {
				count = count - 1;
			};
			if (count > 0) { ^0 } { ^1 };
		}
	}
}

// detects when input values do change (output=1) or are stuck (output=0)

EGM_hasChanged {
	var count;
	var delay;
	var name;
	var last;

	*new { arg delay = 0, name = nil;
		^super.new.init(delay, name);
	}

	init { arg idelay, iname;
		count = 0;
		delay = idelay;
		name = iname;
		last = nil;
	}

	value { arg inval;
		if (inval == last) {
			if (name.notNil && (count <= 0)) {
				name.post;
				": repeated value detected".postln;
			};
			count = delay;
			last = inval;
			^0;
		} {
			if (count > 0) {
				count = count - 1;
			};
			last = inval;
			if (count > 0) { ^0 } { ^1 };
		}
	}
}

// speed limit

EGM_speedlim {
	var speed;
	var count;

	*new { arg speed;
		^super.new.init(speed);
	}

	init { arg ispeed;
		count = 0;
		speed = ispeed;
	}

	value { arg inval;
		if (count <= 0) {
			count = speed;
			^inval;
		} {
			count = count - 1;
			^nil;
		}
	}
}

// keeps value always in interval

EGM_minmaxmap {
	var <min;
	var <max;
	var <>name;
	var <>active;
	var first;

	*new { arg min = 0, max = 1, name = nil;
		^super.new.init(min, max, name);
	}

	init { arg imin, imax, iname;
		min = imin;
		max = imax;
		name = iname;
		active = true;
		first = true;
	}

	value { arg inval;
		if (inval.isKindOf(Array)) {
			if (first) {
				if (min.isKindOf(Array).not) {
					min = Array.fill(inval.size, { min });
				};
				if (max.isKindOf(Array).not) {
					max = Array.fill(inval.size, { max });
				};
				first = false;
			};
			^inval.collect { arg val, i; this.prValueArray(val, i) };
		} {
			^this.prValue(inval);
		};
	}

	prValue { arg inval;
		if (active && (inval > max)) {
			max = inval;
			if (name.notNil) {
				postf("%: new min = %, max = %\n", name, min, max);
			}
		};
		if (active && (inval < min)) {
			min = inval;
			if (name.notNil) {
				postf("%: new min = %, max = %\n", name, min, max);
			}
		};
		if (min == max) {
			^0.5;
		} {
			^((inval - min)/(max - min)).clip(0, 1);
		};
	}

	prValueArray { arg val, i;
		if (active && (val > max[i])) {
			max[i] = val;
			if (name.notNil) {
				postf("%[%]: new min = %, max = %\n", name, i, min[i], max[i]);
			}
		};
		if (active && (val < min[i])) {
			min[i] = val;
			if (name.notNil) {
				postf("%[%]: new min = %, max = %\n", name, i, min[i], max[i]);
			}
		};
		if (min[i] == max[i]) {
			^0.5;
		} {
			^((val-min[i])/(max[i] - min[i])).clip(0, 1);
		}
	}

	getMinMax {
		^[min, max];
	}

	setMinMax { arg minmax;
		min = minmax[0];
		max = minmax[1];
	}
}

// keeps last position (given as vector)

EGM_keepPos {
	var last;

	*new {
		^super.new.init;
	}

	init {
		last = [0, 0, 0];
	}

	value { arg inval;
		var result;
		if (inval.sum == 0) {result = last} {result = inval};
		last = result;
		^result;
	}
}

// computes derivative

EGM_diff {
	var last;

	*new {
		^super.new.reset;
	}

	reset {
		last = 0;
	}

	value { arg inval;
		var result = inval - last;
		last = inval;
		^result;
	}
}

// LeakDC (with coef = 0 equivalent to diff)

EGM_leakdc {
	var x1, y1;
	var coef;

	*new { arg coef;
		^super.new.init(coef).reset;
	}

	init { arg icoef;
		coef = icoef.clip(0, 1);
	}

	reset {
		x1 = 0;
		y1 = 0;
	}

	value { arg x;
		y1 = x - x1 + (y1 * coef);
		x1 = x;
		^y1;
	}
}

// OnePole

EGM_onepole {
	var y1;
	var coef1, coef2;

	*new { arg coef;
		^super.new.init(coef).reset;
	}

	init { arg coef;
		coef1 = coef.clip(-1, 1);
		coef2 = 1 - abs(coef1);
	}

	reset {
		y1 = 0;
	}

	value { arg x;
		y1 = coef2 * x + (coef1 * y1);
		^y1;
	}
}

// computes speed with filtering from position

EGM_speed {
	var diff, median, mean;

	*new { arg median, mean;
		^super.new.init(median, mean).reset;
	}

	init { arg imedian, imean;
		diff = EGM_diff();
		median = EGM_median(imedian);
		mean = EGM_mean(imean);
	}

	reset {
		diff.reset;
		median.reset;
		mean.reset;
	}

	value { arg inval;
		^mean.(median.(diff.(inval).squared.sum.sqrt));
	}
}

// computes velocity (vector)  with filtering from position

EGM_velocity {
	var dim, diff, medians, means;

	*new { arg dim, median, mean;
		^super.new.init(dim, median, mean).reset;
	}

	init { arg idim, imedian, imean;
		dim = idim;
		diff = EGM_diff();
		medians = {EGM_median(imedian)}!dim;
		means = {EGM_mean(imean)}!dim;
	}

	reset {
		diff.reset;
		medians.do(_.reset);
		means.do(_.reset);
	}

	value { arg inval;
		^diff.(inval).collect{arg x, i;
			means[i].(medians[i].(x));
		};
	}
}

// computes speed from position

EGM_pos2speed : EGM_diff {
	value { arg inval;
		^super.value(inval).squared.sum.sqrt;
	}
}

// computes acceleration from position

EGM_pos2accel : EGM_pos2speed {
	var lastSpeed;

	reset {
		super.reset;
		lastSpeed = 0;
	}

	value { arg inval;
		var speed = super.value(inval);
		var result = speed - lastSpeed;
		lastSpeed = speed;
		^result;
	}
}

// computes radius, azimuth, elevation from 2 absolute vectors

EGM_azelabs {

	*new {
		^super.new;
	}

	value { arg o, p;
		var sub = p - o;
		var rad;

		if (sub == [0, 0, 0]) {
			^[0, 0, pi/2];
		} {
			rad = (sub[0].squared+sub[1].squared+sub[2].squared).sqrt;
			^[rad,atan2(sub[1], sub[0]),(sub[2]/rad).acos].drop(1);
		};
	}
}

// computes quaternion from angle axis

EGM_aaToQuat {

	*new {
		^super.new;
	}

	value { arg aa;
		var mag = aa.squared.sum.sqrt;
		var angle = mag / 2;
		var axis = aa / mag;
		var s = sin(angle);
		^[cos(angle)] ++ (axis * s);
	}
}

// computes quaternion conjugate from quaternion

EGM_quatCon {

	*new {
		^super.new;
	}

	value { arg qua;
		var c = -1.0 * qua.drop(1);
		^[qua[0]] ++ c;
	}
}

// computes quaternion multiplication

EGM_quatMul {

	*new {
		^super.new;
	}

	value { arg c, d;
		^[
		(c[0]*d[0])-(c[1]*d[1])-(c[2]*d[2])-(c[3]*d[3]),
		(c[0]*d[1])+(c[1]*d[0])+(c[2]*d[3])-(c[3]*d[2]),
		(c[0]*d[2])-(c[1]*d[3])+(c[2]*d[0])+(c[3]*d[1]),
		(c[0]*d[3])+(c[1]*d[2])-(c[2]*d[1])+(c[3]*d[0])
		];
	}
}

// computes quaternion rotation

EGM_quatRot {
	var m, z;

	*new {
		^super.new.init;
	}

	init {
		m = EGM_quatMul();
		z = EGM_quatCon();
	}

	value { arg c, d;
		^m.(d,m.([0,c].flat,z.(d)));
	}
}

// computes 1 relative radius, azimuth, elevation from 1 relative 3 component angle axis rotation

EGM_azelrel {
	var r, q, a;

	*new {
		^super.new.init;
	}

	init {
		r = EGM_quatRot();
		q = EGM_aaToQuat();
		a = EGM_azelabs();
	}

	value { arg aa, s = [1,0,0];
		var qua = q.(aa);
		var p = r.(s,qua).drop(1);

		^a.([0,0,0],p);
	}
}

// translate and rotate a point into the coordinate system of the originating point

EGM_relCoord {
	var m;
	var z;
	var t;

	*new {
		^super.new.init;
	}

	init {
		m = EGM_quatRot();
		z = EGM_quatCon();
		t = EGM_aaToQuat();
	}

	value { arg c, a, d;
		t.(a);
		^m.((c-d),z.(t.(a))).drop(1);
	}
}

// from MSP biquad~

EGM_biquad {
	var a0, a1, a2, b1, b2, xn_1, xn_2, yn_1, yn_2;

	*new { arg a0, a1, a2, b1, b2;
		^super.new.init(a0, a1, a2, b1, b2);
	}

	*lpsr4 { // low pass filter with cutoff at sr/4, q = 0.7
		^this.new(0.293356, 0.586712, 0.293356, 0, 0.173425);
	}

	*lpsr8 { // low pass filter with cutoff at sr/8, q = 0.7
		^this.new(0.097755, 0.19551, 0.09775, -0.944008, 0.335029);
	}

	*lpsr16 { // low pass filter with cutoff at sr/16, q = 0.7
		^this.new(0.02989, 0.05978, 0.02989, -1.451106, 0.570666);
	}

	*lpsr32 { // low pass filter with cutoff at sr/32, q = 0.7
		^this.new(0.008432, 0.016865, 0.008432, -1.721657, 0.755384);
	}

	*killdc { arg coef = 0.997; // high pass filter
		^this.new(1, -1, 0, coef.neg.clip(-1,0), 0);
	}

	init { arg ia0, ia1, ia2, ib1, ib2;
		a0 = ia0;
		a1 = ia1;
		a2 = ia2;
		b1 = ib1;
		b2 = ib2;
		this.reset;
	}

	reset {
		xn_1 = 0;
		xn_2 = 0;
		yn_1 = 0;
		yn_2 = 0;
	}

	value { arg x;
		var y;

		y = (a0 * x) + (a1 * xn_1) + (a2 * xn_2) - (b1 * yn_1) - (b2 * yn_2);
		xn_2 = xn_1;
		xn_1 = x;
		yn_2 = yn_1;
		yn_1 = y;
		^y;
	}
}

// slide down (kind of peak hold)

EGM_slidedown {
	var slide;
	var last;

	*new { arg slide;
		^super.new.init(slide);
	}

	init { arg islide;
		slide = islide;
		this.reset;
	}

	reset {
		last = nil;
	}

	value { arg inval;
		var result;

		if (inval.notNil) {
			if (last.isNil) {
				last = inval;
			};

			if (inval.isKindOf(Array)) {
				inval.do {arg v, i; if (v > last[i]) { last[i] = v } };
			} {
				if (inval > last) { last = inval; };
			};

			result = last + ((inval - last) / slide);
			last = result;
			^result;
		} {
			^nil;
		}
	}
}

// sliding buffer, outputs every call

EGM_buffer1 {
	var size;
	var array;

	*new { arg size;
		^super.new.init(size);
	}

	init { arg isize;
		size = isize;
		this.reset;
	}

	reset {
		array = Array.fill(size, 0);
	}

	buffer1 { arg inval;
		array = array.drop(1).add(inval);
		^array;
	}

	value { arg inval;
		^this.buffer1(inval);
	}
}

// delay with fixed number of frames

EGM_delay : EGM_buffer1 {
	value { arg inval;
		^super.value(inval)[0];
	}
}

// sliding median

EGM_median : EGM_buffer1 {
	value { arg inval;
		^super.value(inval).median;
	}
}

// sliding mean

EGM_mean : EGM_buffer1 {
	value { arg inval;
		^super.value(inval).mean;
	}
}

// sliding variance

EGM_variance : EGM_buffer1 {
	value { arg inval;
		^super.value(inval).variance();
	}
}

// sliding standard deviation

EGM_stdDev : EGM_buffer1 {
	value { arg inval;
		^super.value(inval).stdDev();
	}
}

// sliding skew

EGM_skew : EGM_buffer1 {
	value { arg inval;
		^super.value(inval).skew();
	}
}

// sliding kurtosis

EGM_kurtosis : EGM_buffer1 {
	value { arg inval;
		^super.value(inval).kurtosis();
	}
}

// evaluate a function only every n calls

EGM_downsample {
	var factor;
	var count;
	var function;
	var last;

	*new { arg factor, function;
		^super.new.init(factor, function);
	}

	init { arg ifactor, ifunction;
		factor = ifactor;
		function = ifunction;
		count = 0;
		last = 0;
	}

	value { arg inval;
		if (count <= 0) {
			count = factor;
			last = function.value(inval);
		};
		count = count - 1;
		^last;
	}
}

// sliding buffer with hopsize, return nil in between hops

EGM_buffer {
	var size, hopSize, index, buffer, input;

	*new { arg size, hopSize, init = 0;
		^super.new.init(size, hopSize, init);
	}

	init { arg isize, ihopSize, iinit;
		size = isize;
		hopSize = ihopSize;
		index = size - hopSize;
		input = Array.fill(size, iinit);
	}

	value { arg inval;
		input[index] = inval;
		index = index + 1;
		if (index >= size) {
			buffer = input.copy;
			input.overWrite(input.copyRange(hopSize, size - 1), 0);
			index = size - hopSize;
			^buffer;
		} {
			^nil;
		};
	}
}

// compute fft from sliding buffer with hopsize
// size has to be a power of two, uses a Hanning window

EGM_fft {
	var size, hopSize, index;
	var real, imag, cosTable, complex, window, spectrum;
	var input;

	*new { arg size, hopSize;
		^super.new.init(size, hopSize);
	}

	init { arg isize, ihopSize;
		size = isize;
		hopSize = ihopSize;
		real = Signal.newClear(size);
		imag = Signal.newClear(size);
		cosTable = Signal.fftCosTable(size);
		window = Signal.hanningWindow(size);
		input = Signal.newClear(size);
		spectrum = nil;
		index = size - hopSize;
	}

	value { arg inval;
		input[index] = inval;
		index = index + 1;
		if (index >= size) {
			index = this.computeBlock;
			^spectrum;
		} {
			^nil;
		};
	}

	computeBlock {
		real = input * window;
		complex = fft(real, imag, cosTable);
		spectrum = complex.magnitude.copyRange(0, (size / 2 - 1).asInteger);
		input.overWrite(input.copyRange(hopSize, size - 1), 0);
		^(size - hopSize);
	}
}

// 2nd order Butterworth lowpass filter
// language version of LPF ugen
// filter is initialised with full bandwidth (i.e. sr/2)
// frequency unit is [0..1] = [0..sr/2]

// inline float32 zapgremlins(float32 x)
// {
// 	float32 absx = std::abs(x);
// 	// very small numbers fail the first test, eliminating denormalized numbers
// 	//    (zero also fails the first test, but that is OK since it returns zero.)
// 	// very large numbers fail the second test, eliminating infinities
// 	// Not-a-Numbers fail both tests and are eliminated.
// 	return (absx > (float32)1e-15 && absx < (float32)1e15) ? x : (float32)0.;
// }

EGM_lpf {
	var y1, y2, a0, b1, b2;
	var slopeLen, slope, a0_slope, b1_slope, b2_slope, reset;

	*new { arg f = 1;
		^super.new.reset.setFreq(f);
	}

	reset {
		y1 = 0;
		y2 = 0;

		a0 = 0;
		b1 = 0;
		b2 = 0;

		reset = true;

		^this;
	}

	setFreq { arg f, s = 120;
		var c = tan(f.clip(0, 1) * pi / 2).reciprocal;
		var c2 = c * c;
		var sqrt2c = c * 1.4142135623731;
		var next_a0, next_b1, next_b2;

		if (reset) {
			reset = false;
			slopeLen = 0;
			slope = 0;

			a0 = (1 + sqrt2c + c2).reciprocal;
			b1 = 2.0.neg * (1.0 - c2) * a0;
			b2 = (1.0 - sqrt2c + c2).neg * a0;
		} {
			slopeLen = s;
			slope = 0;

			next_a0 = (1 + sqrt2c + c2).reciprocal;
			next_b1 = 2.0.neg * (1.0 - c2) * next_a0;
			next_b2 = (1.0 - sqrt2c + c2).neg * next_a0;

			a0_slope = (next_a0 - a0) / slopeLen;
			b1_slope = (next_b1 - b1) / slopeLen;
			b2_slope = (next_b2 - b2) / slopeLen;
		};
	}

	value { arg in;
		var y0 = in + (b1 * y1) + (b2 * y2);
		var out = a0 * (y0 + (y1 * 2) + y2);
		var y0abs = y0.abs;
		var outabs = out.abs;

		if(((outabs > 1e-13) && (outabs < 1e13)).not) { out = 0 };
		if(((y0abs > 1e-13) && (y0abs < 1e13)).not) { y0 = 0 };
		y2 = y1;
		y1 = y0;

		if (slope < slopeLen) {
			a0 = a0 + a0_slope;
			b1 = b1 + b1_slope;
			b2 = b2 + b2_slope;
			slope = slope + 1;
		};

		^out;
	}
}

// two EGM_lpfs in series

EGM_lpf2 {
	var y1a, y2a, y1b, y2b, a0, b1, b2;

	*new { arg f = 1;
		^super.new.reset.setFreq(f);
	}

	reset {
		y1a = 0;
		y2a = 0;
		y1b = 0;
		y2b = 0;

		a0 = 0;
		b1 = 0;
		b2 = 0;

		^this;
	}

	setFreq { arg f;
		var c = tan(f.clip(0, 1) * pi / 2).reciprocal;
		var c2 = c * c;
		var sqrt2c = c * 1.4142135623731;

		a0 = (1 + sqrt2c + c2).reciprocal;
		b1 = 2.0.neg * (1.0 - c2) * a0;
		b2 = (1.0 - sqrt2c + c2).neg * a0;
	}

	value { arg in;
		var y0 = in + (b1 * y1a) + (b2 * y2a);
		var out = a0 * (y0 + (y1a * 2) + y2a);
		var y0abs = y0.abs;
		var outabs = out.abs;

		if(((outabs > 1e-13) && (outabs < 1e13)).not) { out = 0 };
		if(((y0abs > 1e-13) && (y0abs < 1e13)).not) { y0 = 0 };

		y2a = y1a;
		y1a = y0;

		y0 = out + (b1 * y1b) + (b2 * y2b);
		out = a0 * (y0 + (y1b * 2) + y2b);

		y0abs = y0.abs;
		outabs = out.abs;

		if(((outabs > 1e-13) && (outabs < 1e13)).not) { out = 0 };
		if(((y0abs > 1e-13) && (y0abs < 1e13)).not) { y0 = 0 };

		y2b = y1b;
		y1b = y0;

		^out;
	}
}

