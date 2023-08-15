// States of a buffer, alive, freed, movedFrom

SmartImpl_BufferStateAlive {
	var <server;
	var <bufnum;
	var path;
	var <startFrame;
	var <numFrames;
	var <numChannels;
	var <sampleRate;

	// Path is only set when the buffer is loaded from a file.
	// It might be nil. An Optional Type would be nice!
	hasPath { ^path.isNil.not }
	path { ^path !? {""} }
	deletePath { path = nil }

	*new {|server, bufnum, path, startFrame, numFrames, numChannels, sampleRate|
		^super.newCopyArgs(server, bufnum, path, startFrame, numFrames, numChannels, sampleRate)
	}

	checkNotFreed { /* just does nothing, throws if free'd */}

	asString {
		^format("(server: %, bufnum: %, numFrames: %, numChannels: %, sampleRate: %%)",
			server,
			bufnum,
			numFrames,
			numChannels,
			sampleRate,
			if(path.isNil.not, {", path: \"" ++ path ++ "\""}, {""})
		)
	}

	updateFromServer {|frames, channels, rate|
		numFrames = frames;
		numChannels = channels;
		sampleRate = rate;
	}
}

SmartImpl_BufferStateFreed {
	checkNotFreed { this.priv_throw }

	server { this.priv_throw }
	bufnum { this.priv_throw }
	hasPath { this.priv_throw }
	path { this.priv_throw }
	deletePath { this.priv_throw }
	startFrames { this.priv_throw }
	numFrames { this.priv_throw }
	numChannels { this.priv_throw }
	sampleRate { this.priv_throw }
	updateFromServer{this.priv_throw}

	priv_throw {
		Error("This buffer has been free'd and therefore cannot be used or accessed").throw
	}

	asString { ^"(Has been free'd)" }
}

SmartImpl_BufferStateMovedFrom {
	// This is only used if a setting is enabled in smartbuffer.
	// If the osc messages are not assumed to arrive in order, this class is used make things immutable.
	// Could be improved... but isn't enabled by default.
	var methodThatCausedMove;
	*new {|methodThatCausedMove| ^super.newCopyArgs(methodThatCausedMove) }
	checkNotFreed { this.priv_throw }

	server { this.priv_throw }
	bufnum { this.priv_throw }
	hasPath { this.priv_throw }
	path { this.priv_throw }
	deletePath { this.priv_throw }
	startFrames { this.priv_throw }
	numFrames { this.priv_throw }
	numChannels { this.priv_throw }
	sampleRate { this.priv_throw }

	priv_throw {
		Error(format("This Buffer has been altered by the method %,"
			+ "please use the SmartPromise returned from that", methodThatCausedMove)
		).throw
	}


	asString { ^format("(Has been altered by %, use the SmartPromise return from that)", methodThatCausedMove) }
}
