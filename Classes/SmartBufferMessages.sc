SmartHeaderFormats {
	*aiff {^"aiff"}
}

SmartSampleFormats {
	*int24 {^"int24"}
	*float{^"float"}
}

SmartBufferMessages {
	*priv_require{|topMethod ...args|
		args.do({|a| a.isNil.if(
			{("must not be nil, called from " ++ topMethod).error.throw}
		)})
	}

	*readAlloc { |bufnum, path, startFrame=0, numFrames(-1), completionMessage|
		SmartBufferMessages.priv_require(thisMethod, bufnum, path);
		^[
			"/b_allocRead",
			bufnum.asInteger,
			path.asString,
			startFrame.asInteger,
			(numFrames ? -1).asInteger,
			completionMessage
		]
	}

	*read { |bufnum, path, fileStartFrame(0), numFrames(-1), bufStartFrame(0), leaveOpen(0), completionMessage|
		SmartBufferMessages.priv_require(thisMethod, bufnum, path);
		^[
			"/b_read",
			bufnum,
			path,
			fileStartFrame.asInteger,
			numFrames.asInteger,
			bufStartFrame.asInteger,
			leaveOpen.binaryValue,
			completionMessage
		]
	}

	*readAllocChannels {|bufnum, path, startFrame=0, numFrames(-1), channels, completionMessage|
		SmartBufferMessages.priv_require(thisMethod, bufnum, path, channels);
		^[
			"/b_allocReadChannel",
			bufnum.asInteger,
			path.asString,
			startFrame.asInteger,
			(numFrames ? -1).asInteger
		]
		++ channels.asArray.collect(_.asInteger)
		++ [completionMessage] // this one is double wrapped
	}

	*readChannels {|bufnum, path, fileStartFrame(0), numFrames(-1), bufStartFrame(0), leaveOpen(0), channels, completionMessage|
		SmartBufferMessages.priv_require(thisMethod, bufnum, path, channels);
		^[
			"/b_readChannel",
			bufnum,
			path,
			fileStartFrame.asInteger,
			(numFrames ? -1).asInteger,
			bufStartFrame.asInteger,
			leaveOpen.binaryValue
		]
		++ channels.asArray.collect(_.asInteger)
		++ [completionMessage]
	}

	*alloc {|bufnum, numFrames, numChannels, completionMessage|
		SmartBufferMessages.priv_require(thisMethod, bufnum, numFrames, numChannels);
		^[
			"/b_alloc",
			bufnum.asInteger,
			numFrames.asInteger,
			numChannels.asInteger,
			completionMessage
		]
	}

	*free {|bufnum, completionMessage|
		SmartBufferMessages.priv_require(thisMethod, bufnum);
		^[\b_free, bufnum.asInteger, completionMessage]
	}

	*zero {|bufnum, completionMessage|
		SmartBufferMessages.priv_require(thisMethod, bufnum);
		^[\b_zero, bufnum.asInteger, completionMessage]
	}

	*normalize {|bufnum, newmax=(1), asWavetable|
		SmartBufferMessages.priv_require(thisMethod, bufnum);
		^[
			"/b_gen",
			bufnum.asInteger,
			asWavetable.asBoolean.if({"wnormalize"}, {"normalize"}),
			newmax.asFloat
		]
	}

	*get {|bufnum, index|
		SmartBufferMessages.priv_require(thisMethod, bufnum, index);
		^["/b_get", bufnum.asInteger, index.asInteger]
	}

	*getN {|bufnum, index, count|
		SmartBufferMessages.priv_require(thisMethod, bufnum, index, count);
		^["/b_getn", bufnum.asInteger, index.asInteger, count.asInteger]
	}

	*set {|bufnum ...indexValuePairs|
		SmartBufferMessages.priv_require(thisMethod, bufnum, indexValuePairs[0], indexValuePairs[1]);
		^["/b_set", bufnum.asInteger]
		++ indexValuePairs.collect{|n,i| if(i.even, {n.asInteger}, {n.asFloat}) }
	}

	*fill {|bufnum... fromCountValues|
		SmartBufferMessages.priv_require(thisMethod, bufnum, fromCountValues[0], fromCountValues[1], fromCountValues[2]);
		^["/b_fill", bufnum.asInteger]
		++ fromCountValues.collect{ |n,i| if(i % 3 == 3, {n.asFloat}, {n.asInteger}) };
	}

	*writeToPath { arg
		bufnum, path,
		headerFormat=(SmartHeaderFormats.aiff),
		sampleFormat=(SmartSampleFormats.int24),
		numFrames=(-1),
		startFrame=0,
		leaveOpen=false,
		completionMessage;

		SmartBufferMessages.priv_require(thisMethod, bufnum, path);
		^[
			"/b_write",
			bufnum.asInteger,
			path.asString,
			headerFormat.asString,
			sampleFormat.asString,
			numFrames.asInteger,
			startFrame.asInteger,
			leaveOpen.binaryValue,
			completionMessage
		]
	}
}

