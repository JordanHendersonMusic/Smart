// interface - has 'private implementation' methods
SmartAwaitableInterface {

	pimpl_awaitNoReturn {|timeoutbeats| this.subclassResponsibility(thisMethod) }

	pimpl_addToActions { |thing| ^this.subclassResponsibility(thisMethod) }

	await { |timeoutbeats=3|
		var r = this.pimpl_awaitNoReturn(timeoutbeats);
		^if(r.isKindOf(Collection),
			{ r.collect(_.return) },
			{ r.return });
	}

	then { |action|
		this.pimpl_addToActions(action);
		^this
	}

	catch { |exceptionTypesToCatch, action|
		this.pimpl_addToActions(SmartCatchFunction(exceptionTypesToCatch, action));
		^this
	}

	doWith { |action| ^this.then({|i| action.(i); i }) }

	thenCleanup {|then, cleanup|
		^this.then({|self|
			var r = SmartAwaitableInterface.awaitIfNeeded( then.(self) );
			cleanup.(self);
			r
		})
	}

	shallowCopy { this.shouldNotImplement(thisMethod) }
	deepCopy { this.shouldNotImplement(thisMethod) }
	copy { this.shouldNotImplement(thisMethod) }

	*awaitIfNeeded { |p| ^if(p.isKindOf(SmartAwaitableInterface), {p.await}, {p}) }

	*getSafeTimeoutBeat { |beatPoint|
		// just get me a positive number...
		if(beatPoint < 0,
			{Error(format("Beat point cannot be negative, %.", beatPoint)).throw});
		if((beatPoint.isKindOf(Integer).not) and: (beatPoint.isKindOf(Float).not),
			{Error("Beat point must be float or integer.").throw});
		if(beatPoint.isNaN,
			{Error("Beat point cannot be nan.").throw});
		if(beatPoint === inf,
			{Error("Beat point cannot be in.f").throw});
		^beatPoint;
	}
}
