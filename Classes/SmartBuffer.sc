//Minimal Constructors: read(path), readChannels(path, channels), loadCollection(

SmartBuffer {
	var maybeAliveBufferInfo;
	classvar priv_serverUpdators;
	classvar <>assumeServerCommandsArriveInOrder = true;

	*initClass { priv_serverUpdators = IdentityDictionary.new() }

	//constructors
	*read {|path, server, startFrame=0, optNumFrames|
		var safe_path = PathName(path.asString).isFile.not.if(
			{ SmartErrorInvalidAudioFile().throw },
			{ path.asString }
		);
		var safe_server = SmartBuffer.priv_getSafeServerAndEnsureUpdaterExists(server);
		var safe_numFrames = optNumFrames ? -1;
		var safe_startFrame = startFrame.asInteger;
		var bufnum = safe_server.nextBufferNumber(1);
		var bufInfo = SmartImpl_BufferStateAlive(
			safe_server, bufnum, safe_path, safe_startFrame, safe_numFrames, nil, nil
		);
		var realBuffer = super.newCopyArgs(bufInfo);
		var promise = SmartPromise();

		priv_serverUpdators[safe_server].registerBufferAndAction(bufnum, realBuffer, {
			|realBuffer2, nframes, nchannels, nrate|
			bufInfo.updateFromServer(nframes, nchannels, nrate);
			promise.fulfil(realBuffer);
		});

		safe_server.listSendMsg(SmartBufferMessages.readAlloc(
			bufnum, safe_path, safe_startFrame, safe_numFrames, ["/b_query", bufnum]
		));

		^promise
	}

	*readChannels {|path, channels, server, startFrame=0, optNumFrames=(-1)|
		var safe_path = PathName(path.asString).isFile.not.if(
			{ SmartErrorInvalidAudioFile().throw },
			{ path.asString }
		);
		var safe_server = SmartBuffer.priv_getSafeServerAndEnsureUpdaterExists(server);
		var safe_numFrames = optNumFrames ? -1;
		var safe_startFrame = startFrame.asInteger;
		var safe_channels = channels.isNil.if(
			{Error("Must provide a channels value").throw},
			{channels}
		);
		var bufnum = safe_server.nextBufferNumber(1);
		var bufInfo = SmartImpl_BufferStateAlive(
			safe_server, bufnum, safe_path, safe_startFrame, safe_numFrames, nil, nil
		);
		var realBuffer = super.newCopyArgs(bufInfo);
		var promise = SmartPromise();

		priv_serverUpdators[safe_server].registerBufferAndAction(bufnum, realBuffer, {
			|realBuffer2, nframes, nchannels, nrate|
			bufInfo.updateFromServer(nframes, nchannels, nrate);
			promise.fulfil(realBuffer);
		});

		safe_server.listSendMsg(SmartBufferMessages.readAllocChannels(
			bufnum, safe_path, safe_startFrame, safe_numFrames, safe_channels, ["/b_query", bufnum]
		));

		^promise
	}

	*loadCollection {|collection, server, sampleRate|
		^SmartPromise.fulfilWith({
			var safe_collection = SmartBuffer.priv_getSafeCollection(collection);
			// ensures [ [s11,s12...], [s21, s22...] ...]
			var safe_server = SmartBuffer.priv_getSafeLocalServer(server, thisMethod);
			var safe_numChannels = safe_collection.shape[0];
			var safe_sampleRate = sampleRate ? safe_server.sampleRate;
			var sndfile = SoundFile.new.sampleRate_(safe_sampleRate).numChannels_(safe_numChannels);
			var path = PathName.tmp ++ sndfile.hash.asString;
			// file io
			if(sndfile.openWrite(path).not, { SmartErrorCouldNotOpenFile().throw });
			sndfile.writeData(safe_collection);
			sndfile.close;

			protect {
				SmartBuffer.read(path, safe_server)
				.doWith(_.priv_deletePath)
			} {
				if(File.delete(path).not, {format("Could not delete intermediate file in %", thisMethod).warn})
			}
		})
	}

	// basic getters
	server { ^this.priv_info.server}
	bufnum { ^this.priv_info.bufnum }
	path { ^this.priv_info.path }
	startFrame { ^this.priv_info.startFrame }
	numFrames { ^this.priv_info.numFrames }
	numChannels { ^this.priv_info.numChannels }
	sampleRate { ^this.priv_info.sampleRate }
	duration { ^this.priv_info.numFrames / this.priv_info.sampleRate }

	free {
		this.priv_info.server.listSendMsg(
			SmartBufferMessages.free(this.priv_info.bufnum)
		);
		maybeAliveBufferInfo = SmartImpl_BufferStateFreed(); // the dead buffer state
	}


	read { |path, fileStartFrame(0), numFrames(-1), bufStartFrame(0), leaveOpen(0)|
		^this.priv_accoutForOutOfOrderMessages(
			SmartBufferMessages.read(
				this.priv_info.bufnum,
				path,
				fileStartFrame,
				numFrames,
				bufStartFrame,
				leaveOpen
			)
		)
	}

	readChannels { |path, fileStartFrame(0), numFrames(-1), bufStartFrame(0), leaveOpen(0), channels|
		^this.priv_accoutForOutOfOrderMessages(
			SmartBufferMessages.readChannels(
				this.priv_info.bufnum,
				path,
				fileStartFrame,
				numFrames,
				bufStartFrame,
				leaveOpen,
				channels
			)
		)
	}

	zero {
		^this.priv_accoutForOutOfOrderMessages(
			SmartBufferMessages.zero(this.priv_info.bufnum)
		)
	}

	normalize { |newmax=1, asWavetable=false|
		^this.priv_accoutForOutOfOrderMessages(
			SmartBufferMessages.normalize(this.priv_info.bufnum, newmax, asWavetable)
		)
	}

	get { |index|
		var promise = SmartPromise();
		this.priv_OSCFuncInfoFork(
			msg: SmartBufferMessages.get(this.priv_info.bufnum, index),
			responseMsg: \b_set, // [/b_set, bufnum, index, value]
			action: {|msg| promise.fulfil(msg[3]) },
			argTemplate:  [this.priv_info.bufnum, index.asInteger]
		);
		^promise;
	}

	getN { |index, count|
		var promise = SmartPromise();
		if(count > 100, { // too big - number arbitary
			^SmartPromise.fulfilWith({
				this.getAllAsArray.then( _[index..(index + count)] )
			});
		});
		promise = SmartPromise();
		this.priv_OSCFuncInfoFork(
			msg: SmartBufferMessages.getN(this.priv_info.bufnum, index, count),
			responseMsg: \b_setn, // [/b_setn, bufnum, starting index, length, ...sample values]
			action: {|msg| promise.fulfil(msg[4..]) },
			argTemplate:  [this.priv_info.bufnum, index.asInteger]
		);
		^promise;
	}

	doThenFree {|action|
		var r = action.(this);
		this.free;
		^r
	}

	at { |index| ^this.get(index) }

	set {|index, value ... morePairs|
		^this.priv_accoutForOutOfOrderMessages(
			SmartBufferMessages.set(
				this.priv_info.bufnum,
				index.asInteger,
				value.asFloat,
				*morePairs.collect({|n,i| if(i.even, {n.asInteger}, {n.asFloat}) })
			)
		)
	}

	setN {}

	fill {|from, count, value ...moreFromCountValues|
		^this.priv_accoutForOutOfOrderMessages(
			SmartBufferMessages.fill(
				this.priv_info.bufnum,
				from, count, value,
				*moreFromCountValues
			)
		)
	}

	copySeries { |first, second, last|
		var indicies = (first, second..(last ? (this.priv_info.numFrames - 1)));
		//sequential access
		if(second.isNil, { ^this.getn(first, (last ? this.priv_info.numFrames) - (first + 1)) });

		if (indicies.size > 20, {
			// non-sequential, get all
			^SmartPromise.fulfilWith({
				this.getAllAsArray.await.copySeries(first, second, last)
			})
		}, {
			// many small gets
			^SmartBarrier.fill(indicies.size,  {|index|
				this.get(indicies[index])
			})
		});
	}

	writeTo {|path, headerFormat, sampleFormat, numFrames(-1), startFrame(0), leaveOpen(false), completionMessage|
		var safe_header = headerFormat ? SmartHeaderFormats.aiff;
		var safe_path = path ?? {
			thisProcess.platform.recordingsDir +/+ "SC_" ++ Date.localtime.stamp ++ "." ++ safe_header.asString
		};
		this.priv_info.server.listSendMsg(
			SmartBufferMessages.writeToPath(
				this.priv_info.bufnum,
				safe_path,
				safe_header.asString,
				sampleFormat ? SmartSampleFormats.float,
				numFrames ? this.priv_info.numFrames,
				startFrame,
				leaveOpen.binaryValue,
				completionMessage
			)
		)
	}

	getAllAsArray {
		// no need for index and count, just use buffer[23..] or something
		^if(this.priv_info.server.isLocal,
			{ this.priv_getAllAsArrayLocal },
			{ this.priv_getAllAsArrayNonLocal }
		)
	}

	priv_getAllAsArrayLocal {
		^SmartPromise.fulfilWith({
			// this does not work with multichannel files... doesn't appear the original did either!
			var path = PathName.tmp ++ this.hash.asString;
			var array;
			var file = SoundFile.new;

			this.writeTo(
				path,
				SmartHeaderFormats.aiff,
				SmartSampleFormats.float,
				this.priv_info.numFrames,
				0.binaryValue,
				false
			);

			this.priv_info.server.sync;

			protect {
				if(file.openRead(path).not, { SmartErrorCouldNotOpenFile().throw });
				array = FloatArray.newClear(file.numFrames * file.numChannels);
				file.readData(array);
				/* return */ array
			} {
				file.close;
				if(File.delete(path).not) { ("Could not delete data file:" + path).warn };
			}
		})
	}

	priv_getAllAsArrayNonLocal {
		^SmartPromise.fulfilWith(
			func: {
				var index = 0;
				var step = 1633; // this is from Buffer - apparently it works...
				var count = this.priv_info.numFrames * this.priv_info.numChannels;
				var out = FloatArray.newClear(count);

				while {index < this.priv_info.numFrames} {
					var thisCount = min(step, count - index);
					out = out.overWrite(this.getN(index, thisCount).await, index);
					index = index + thisCount;
				};

				out
			},
			timeOutBeats: 5
		)
	}

	plot {|name, bounds, minval, maxval, separately(false)|
		var plotter = [0].plot(
			name ? "Buffer plot (bufnum: %)".format(this.bufnum),
			bounds, minval: minval, maxval: maxval
		);
		var unitStr = if (this.priv_info.numChannels == 1) { "samples" } { "frames" };
		var arr = this.getAllAsArray();
		defer {
			plotter.setValue(
				arr.await.unlace(this.priv_info.numChannels),
				findSpecs: true,
				refresh: false,
				separately: separately,
				minval: minval,
				maxval: maxval
			);
			plotter.domainSpecs = ControlSpec(0.0, this.priv_info.numFrames, units: unitStr);
			if(this.priv_info.numChannels > 4) { plotter.axisLabelX_(nil) };
		};
		^plotter
	}

	// conversions/interfaces
	asUGenInput { ^this.priv_info.bufnum }
	asControlInput { ^this.priv_info.bufnum }
	asString { 	^format("SmartBuffer%", this.priv_info.asString) }

	asBuffer {
		warn(
			"There should NEVER be one server buffer managed by two 'Buffer' classes."
			+ "Make sure the old one is removed or there will be double frees!"
		);
		^Buffer(
			this.priv_info.server,
			this.priv_info.numFrames,
			this.priv_info.numChannels,
			this.priv_info.bufnum
		)
		.sampleRate_(this.priv_info.sampleRate)
		.path_(this.priv_info.path)
		.startFrame_(this.priv_info.startFrame)
	}

	*newFromBuffer {|buf|
		buf.isKindOf(Buffer).not.if(
			{ SmartErrorTypeMismatch().throw }
		);
		warn(
			"There should NEVER be one server buffer managed by two *Buffer classes."
			+ "Make sure the old one is not free'd or there will be double frees!"
		);
		^super.newCopyArgs(
			SmartImpl_BufferStateAlive(
				buf.server,
				buf.bufnum,
				buf.path,
				buf.startFrame,
				buf.numFrames,
				buf.numChannels,
				buf.sampleRate
			)
		)
	}

	// priv stuff
	*priv_getSafeServer {|s| ^s ? Server.default }
	*priv_getSafeLocalServer {|s, callingMethod|
		var ser = s ? Server.default ;
		if (ser.isLocal.not,
			{SmartErrorNotLocalServer().throw})
		^ser
	}

	*priv_getSafeServerAndEnsureUpdaterExists {|s|
		var server = SmartBuffer.priv_getSafeServer(s);
		priv_serverUpdators[server] ?? {
			priv_serverUpdators[server] = SmartImpl_ServerBufferUpdater(server)
		};
		^server
	}

	*priv_getSafeCollection {|collection|
		var arr = collection.asArray.as(FloatArray);
		var t = if (arr.rank > 2, {
			SmartErrorCannotHoldNestedArray().throw
		});
		if (arr.rank == 1, { [arr] }, { arr } )// flat array should be wrapped
	}

	priv_deletePath { this.priv_info.deletePath }

	priv_info {
		try {
			maybeAliveBufferInfo.server.serverRunning.not.if(
				{ maybeAliveBufferInfo = SmartImpl_BufferStateFreed() }
			);
		}{};
		^maybeAliveBufferInfo
	}

	priv_OSCFuncInfoFork { |msg, responseMsg, action, argTemplate|
		fork {
			OSCFunc(
				action,
				responseMsg,
				this.priv_info.server.addr,
				argTemplate: argTemplate
			).oneShot;

			this.priv_info.server.listSendMsg(msg);
		}
	}

	priv_accoutForOutOfOrderMessages {|msg|
		if(assumeServerCommandsArriveInOrder) {
			this.server.listSendMsg(msg);
			^SmartPromise.newFulfilled(this) // just to complete the interface
		} {
			// If the comamnds do not arrive in order,
			// then await must be call after every mutation, and before every access.
			// If a buffer is stored in a varible, there is no way to inforce this
			// (well extra state could be added), so the old one is invalidated.
			// Probably better to use TCP that do this.
			var copy = this.copy();
			maybeAliveBufferInfo = SmartImpl_BufferStateMovedFrom(thisMethod);
			^SmartPromise.fulfilWith({
				copy.server.listSendMsg(msg);
				copy.server.sync;
				copy;
			})
		}
	}
}


SmartErrorCannotHoldNestedArray : Exception {
	errorString {
		^"ERROR: SmartBuffer cannot hold matricies or tensors,"
		+ "please provide an array in the form"
		+ "[ [L0, L1, L2...], [R0, R1, R2...], ...]";
	}
}

SmartErrorNotLocalServer : Exception {
	errorString {^"ERROR: Server must be local" }
}

SmartErrorTypeMismatch : Exception {
	errorString {^"ERROR: Cannot create SmartBuffer with class, needs Buffer" }
}

SmartErrorCouldNotOpenFile : Exception {
	errorString {^"ERROR: Could not open sound file" }
}
SmartErrorInvalidAudioFile : Exception {
	errorString {^"ERROR: Path not a sound file" }

}


