// stores buffers that are in the process of being updated and calls some action when \b_info received - used to read an audio file

SmartImpl_ServerBufferUpdater {
	var server;
	var dictOfBuffersToUpdate;
	var dicOfActionsPerBuffer;
	var oscfunc;

	*new {|server| ^super.newCopyArgs(server, (), ()).priv_init }

	registerBufferAndAction {|bufnum, buffer, action|
		dictOfBuffersToUpdate[bufnum] !? { ^"buffer already registered!".throw };
		dictOfBuffersToUpdate[bufnum] = buffer;
		action !? { dicOfActionsPerBuffer[bufnum] = action };
		^this
	}

	priv_init {
		oscfunc = OSCFunc({ |msg|
			var b = dictOfBuffersToUpdate[msg[1]];
			var action = dicOfActionsPerBuffer[msg[1]];
			b !? {
				action !? {
					action.(b, *msg[2..]);
					dicOfActionsPerBuffer[msg[1]] = nil;
				}
			}
		}, \b_info, server.addr)
		.permanent;
		^this
	}
}