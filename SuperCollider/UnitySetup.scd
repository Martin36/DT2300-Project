
// Klicka på parentesen och sen exekvera så körs alla rader samtidigt, man måste exekvera OSCdef längst ner först också så det finns någon mottagare för /Startrec meddelandet

(
	m.wScene[1].doAction; // add a document
	m.documents.asArray[0].view.vTAddB.doAction; // add track in document 0
	m.documents.asArray[0].tracks[0].view.vTrRecTargetNB.valueAction_(1);  // set tracking target
	m.documents.asArray[0].tracks[0].view.vPlTargetNB.valueAction_(1);  // set retracing tracking target
	//m.documents.asArray[0].tracks[0].view.vTrRecChannelNB.valueAction_(1); // set audio channel
	m.documents.asArray[0].tracks[0].view.vPlOnB.valueAction_(true);   // switch on retracing
)

OSCdef(\Startrec, { | msg |
        { m.documents.asArray[0].tracks[0].view.vTrRecB.valueAction_(msg[1] == 1) }.defer;
}, '/Startrec');