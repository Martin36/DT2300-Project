TrBufferPool {
	var <pool;
	var <booked;

	// new pool with n buffers of length l (in s)
	*new { arg n = 12, l = 60;
		^super.new.init(n, l);
	}

	init { arg n, l;
		pool = Array.fill(n, { Buffer.alloc(Server.default, l * Server.default.sampleRate ) });
		booked = Array.fill(n, false);
	}

	free {
		pool.do(_.free);
	}

	// get a free buffer, return buffer or nil
	book {
		booked.do{|x, i|
			if (x == false) {
				booked[i] = true;
				^pool[i];
			}
		};
		("TrBufferPool::book: no buffer free").warn;
		^nil
	}

	// release a booked buffer, return true or false
	release { arg b;
		pool.do{|x, i|
			if (x == b) {
				if (booked[i] != true) {
					("TrBufferPool::release: buffer " ++ i ++ " was not booked").warn;
					^false;
				} {
					booked[i] = false;
					// x.zero; // clicks
					^true;
				}
			}
		};
		("TrBufferPool::release: buffer not found").warn;
		^false;
	}
}