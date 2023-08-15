SmartBarrier : SmartAwaitableInterface  {
	var promises;
	var state;

	*new {|funcs|
		^super.newCopyArgs(
			funcs.collect({|f, i|
				// call each function with its index
				SmartPromise.fulfilWith({ f.(i) })
			}),
			\unfulfilled,
			nil
		)
	}

	*fill{ |n, func| ^SmartBarrier.new( {func}!n ) }

	pimpl_awaitNoReturn {|timeoutbeats|
		^case
		{ state == \hasReturned } {
			promises = nil;
			Error("Cannout await twice").throw
		}
		{ state == \timeout } {
			state = \hasReturned;
			promises = nil;
			SmartErrorTimeout().throw;
		}
		{
			var out = nil!promises.size;
			var count = promises.size;
			var awaiting = thisThread;
			state = \hasReturned;

			promises.do({|p, i|
				fork {
					try {
						out[i] = p.pimpl_awaitNoReturn(timeoutbeats);
					} {
						|ex|

						out[i] = SmartSomeException(ex);
					};
					count = count - 1;
					if(count == 0,
						{awaiting.clock.sched(0, awaiting)})
				}
			});

			while {count != 0} {
				\await.yield
			};

			out
			.reject(_.isKindOf(SmartSomeIgnore));
		}
	}

	pimpl_addToActions {|thing|
		if(state == \hasReturned, {Error("No then after await").throw});
		promises.do{|p| p.pimpl_addToActions(thing) };
		^this
	}

	reject { |filter|
		if(state == \hasReturned, {Error("No then after await").throw});
		this.pimpl_addToActions( {|v| if(filter.(v), {SmartSomeIgnore()}, {v}) } );
		^this
	}

	select { |filter|
		^this.reject({|i| filter.(i).not })
	}
}
