// held value wrappers
SmartSome {}
SmartSomeException : SmartSome {
	var h;
	*new{ |h|
		^case {h.isKindOf(SmartSome)} {h}
		// the current awaitable cannot catch SmartErrorTimeout.
		// the only way it can occur is if something inside it timedout
		{h.isKindOf(SmartErrorTimeout)} {super.newCopyArgs(SmartErrorNestedTimeout())}
		{super.newCopyArgs(h)}
	}
	get { ^h }
	return { ^h.throw }
}

SmartSomeValue : SmartSome {
	var v;
	*new{ |v|
		^case {v.isKindOf(SmartSome)} {v}
		{super.newCopyArgs(v)}
	}
	get { ^v }
	return { ^v }
}

SmartSomeIgnore : SmartSome {
	// no constructer as just deletes value
	get { ^this }
	return { ^this }
}



// catch function
SmartCatchFunction {
	var exceptionsTypesCaught;
	var func;
	*new {|ex, func|
		var ar = ex !? {ex.asArray} ?? { [Object] };
		if(ar.includes(SmartErrorTimeout),
			{Error("Cannot have SmartErrorTimeout as a catch").throw	},
			{ ^super.newCopyArgs(ar, func) }
		)
	}
	value {|v| ^if (exceptionsTypesCaught.any(v.isKindOf(_)), {func.(v)}, {v} ) }
}





// outline of states
//                                                       |-> \timeout
//   \unfulfilled -> [\doingActions -> \fulfilled].loop -|
//                                                       |-> \hasReturned

SmartPromise : SmartAwaitableInterface {
	var held;
	var arrayOfActions;
	var state;
	var awaitingThread;

	*new { ^SmartPromise.priv_new(nil, [], \unfulfilled, nil) }

	*newFulfilled { |value|
		var self = SmartPromise.priv_new(SmartSomeValue(value), [], \fulfilled, nil);
		^self;
	}

	*fulfilWith {|func|
		var self = SmartPromise
		.priv_new(nil, [], \unfulfilled, nil)
		.then(func);
		fork { self.fulfil(nil) };
		^self
	}

	fulfil { |value|
		held = SmartSomeValue(value);
		forkIfNeeded { // if being set from an osc thread this is needed
			if(state == \fulfilled, { Error("Cannot fulfil twice").throw });

			state = \doingActions;
			held = this.priv_consumeActions(held);
			if( state != \timeout ){ state = \fulfilled };

			this.priv_awakeWaitingThread
		}
	}

	// priv/pimpl
	*priv_new { |held, arrayOfActions, state, awaitingThread|
		^super.newCopyArgs(held, arrayOfActions, state, awaitingThread)
	}

	pimpl_awaitNoReturn { |timeoutbeats=3|
		^case
		{ state == \hasReturned } {
			held = nil;
			Error("Cannout await twice").throw
		}
		{ state == \timeout } {
			state = \hasReturned;
			held = nil;
			SmartErrorTimeout().throw;
		}
		{ (state == \fulfilled) and: arrayOfActions.isEmpty } {
			state = \hasReturned;
			held // exit
		}
		{ (state == \fulfilled) and: arrayOfActions.isEmpty.not } {
			var startBeat, endBeat;
			startBeat = thisThread.beats;
			state = \doingActions;
			held = this.priv_consumeActions(held);
			// this might wait, so more actions could be added...unlikely
			if( state != \timeout ){ state = \fulfilled };
			endBeat = thisThread.beats;
			this.pimpl_awaitNoReturn((timeoutbeats - (endBeat - startBeat)).clip(0, inf))
		}
		{ (state == \unfulfilled) or: (state == \doingActions) } {
			awaitingThread = thisThread;

			fork {
				SmartAwaitableInterface.getSafeTimeoutBeat(timeoutbeats).wait;
				if (state != \hasReturned) {
					state = \timeout;
					//always valid, method only called from await.
					awaitingThread.clock.sched(0, awaitingThread)
				}
			};

			\awaitingSymbol.yield ;// here is the wait

			case
			{ state == \timeout } {
				state = \hasReturned;
				held = nil;
				SmartErrorTimeout().throw;
			}
			{ (state == \fulfilled) and: arrayOfActions.isEmpty } {
				state = \hasReturned;
				held // exit
			}
			{ Error("Internal error in SmartPromise - not all actions were consumed").throw }
		}
		{ Error("Internal error in SmartPromise - fallthrough").throw }
	}

	pimpl_addToActions {|thing|
		if(state == \hasReturned) { Error("Cannot add then once await has returned").throw };

		arrayOfActions = arrayOfActions ++ thing;

		if(state == \fulfilled, {
			state = \doingActions;
			fork {
				held = this.priv_consumeActions(held);
				if( state != \timeout ){ state = \fulfilled };
				this.priv_awakeWaitingThread
			}
		});
		^this
	}

	priv_awakeWaitingThread { awaitingThread !? { awaitingThread.clock.sched(0, awaitingThread) } }

	priv_consumeActions {|v|
		var i = 0;
		while {(i < arrayOfActions.size) and: (state != \timeout)} {
			var act = arrayOfActions[i];
			i = i + 1;
			try  {
				case
				{v.isKindOf(SmartSomeValue)} {
					v = SmartSomeValue(SmartAwaitableInterface.awaitIfNeeded(act.( v.get )))
				}
				{v.isKindOf(SmartSomeException) and: act.isKindOf(SmartCatchFunction)} {
					v = SmartSomeValue(SmartAwaitableInterface.awaitIfNeeded(act.( v.get )))
				}
				// otherwise skip
			} { |ex|
				v = SmartSomeException(ex)
			}
		};
		// either, all have been iterated through or the state timed out - delete them!
		arrayOfActions = [];
		^v
	}
}


SmartErrorTimeout : Exception {
	errorString { ^"ERROR: SmartPromise took too long." }
}

SmartErrorNestedTimeout : Exception {
	errorString { ^"ERROR: One of the nested SmartPromises took too long." }
}




