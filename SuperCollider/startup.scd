"

*** TRACE LOCALHOST STEREO STARTUP LOADED ***

".postln;

fork( {
	Server.default = s = Server.local;

	s.quit;
	0.5.wait;
	Server.killAll;
	0.5.wait;
	s.options
	.numOutputBusChannels_(2)
	.numInputBusChannels_(2)
	.numAudioBusChannels_(1024)
	.numControlBusChannels_(4096)
	.memSize_(8192 * 32)
	.hardwareBufferSize_(512)
	.numWireBufs_(512)
	.maxNodes_(4096)
	;
	//CHANGE THIS TO CORRECT PATH!
	s.waitForBoot({{"C:/Users/martin/AppData/Local/SuperCollider/ReTrace.scd".load}.defer});

}, AppClock);
