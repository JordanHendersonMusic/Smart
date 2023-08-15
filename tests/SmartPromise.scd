(
~p = SmartPromise(1);
~p.fulfil(12);
{ ~p.await.postln }.fork
)


(
~p = SmartPromise(1);
{ ~p.await.postln }.fork;
~p.fulfil(232);

)


(
~p = SmartPromise().then(_ * 2);
{ ~p.await.postln }.fork;
~p.fulfil(2);

)

(
~p = SmartPromise();
~p.then(_ * 2);
~p.fulfil(2);
~p.then(_ + 1);
{ ~p.await.postln }.fork;
)

(
~p = SmartPromise().then(_ * 2);
{
	\1.postln;
	~p.await.postln;
	\2.postln;
}.fork;
\a.postln;
~p.fulfil(2);
\b.postln;
~p.then("result"); // not clear that is evaluated, but await hasn't
\c.postln;

// prints a b c (returns from this routine) 1 result 2
)

(
fork {
	~p = SmartPromise.fulfilWith({
		1.0.wait;
		"init1".postln
	})
	.then({|i| i + "then2".postln })
	.then({|i| i + "then3".postln });

	"pre await".postln;
	~r = ~p.await(5).postln;
	"post await".postln;

}
)


(
fork {
	~p = SmartPromise()
	.then(_ + "then1")
	.then(_ + "then2");

	"pre await".postln;

	fork { 2.0.wait; ~p.fulfil("init"); };

	~r = ~p.await.postln;
	"post await".postln;
}
)



(
fork {
	~p = SmartPromise()
	.then(_ + "then1")
	.then(_ + "then2");

	"pre await".postln;

	~p.fulfil("init");



	fork {
		~error = false;
		1.0.wait;
		try { ~p.then(_ + "then3")  }
		{ ~error = true; };
		if(~error.not, {Error("asdfasdf!").throw});
	}; // throws, await was called before then


	~r = ~p.await.postln;

	"post await".postln;

}
)



(

fork {
	~p = SmartPromise()
	.then(_ + "then1")
	.then(_ + "then2");

	~p.fulfil("init");

	~p.then(_ + "then3"); // then always forks

	"pre await".postln;
	~r = ~p.await.postln;
	"post await".postln;
};

)


(
fork {
	~p = SmartPromise.fulfilWith({
		1.0.wait;
		"init"
	})
	.then(_ + "then1")
	.then(_ + "then2")
	.then({ Error("a").throw });

	"pre await".postln;
	~r = ~p.await; // should throw!
	"post await".postln;
}
)

(
fork {
	~p = SmartPromise.fulfilWith({
		1.0.wait;
		"init"
	})
	.then(_ + "then1")
	.then(_ + "then2")
	.then({ Error("a").throw })
	.catch(Error, { "got error" });

	"pre await".postln;
	~r = ~p.await.postln;
	"post await".postln;
}
)

(
fork {
	~p = SmartPromise.fulfilWith({
		1.0.wait;
		"init"
	})
	.then(_ + "then1")
	.then(_ + "then2")
	.then({ Error("a").throw })
	.catch(MethodError, { "got error" });

	"pre await".postln;
	~r = ~p.await(0.1).postln; // should throw
	"post await".postln;
}
)






(
fork {
	~b = SmartBarrier.fill(10, {|i|
		3.rand.wait;
		("init" + i).postln;
		i
	} )
	.then({|i| format("mul%", i).postln; i * 2 })
	.reject({|r| ("reject" + (r/2)).postln; r > 10})
	.await
	.postln;
}
)

(

s.waitForBoot {
	~p = "/home/jordan/Music/sc/lettuce.wav";
	~bp = SmartBuffer.read(~p).then(_.get(234453));

	\pre.postln;
	~b = ~bp.await.postln;
	\post.postln;
}
)


(
s.waitForBoot {

	~p = "/home/jordan/Music/sc/lettuce.wav";

	~bp = SmartBarrier(
		10.collect{   SmartBuffer.read(~p)   }
	)
	.then(_.normalize)
	.thenCleanup(_[44100 + 1000.rand], _.free)
	.reject( _ > 0 );


	\pre.postln;
	~bs = ~bp.await.postln;
	\post.postln;

}
)

ArrayedCollection


Exception

Object
Buffer














(


try {
	Error(\e).throw
} {|a|
	a.throw
}

)














