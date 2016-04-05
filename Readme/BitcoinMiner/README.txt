READ ME file for Distributed Operating Systems - Project 1
Date: September 13th, 2015

Group members:

1. Satish Erappa, UFID: 85975669, hsitas444@ufl.edu
2. Vikaasa Ramdas Thandu Venkat Kumar, UFID: 44005810, vikaasa@ufl.edu

Source Files Location:

src/main/scala-2.11/project1.scala

Remote Actor Setup:

1. 	The project directory has been uploaded in a .ZIP file.
	cd to the project directory. 
2. 	Open src/main/resources/application.conf
3.	Edit hostname field in the file to the ipaddress of the current working machine.
4. 	Make sure that the provider field is set to akka.remote.RemoteActorRefProvider

Usage:

1. 	FOR LOCAL IMPLEMENTATION:
	sbt "run (no of leading zeroes)[resume_flag]"

	where (no of leading zeroes) is an integer which will be the required number of prefixed 0’s in the generated bitcoin.
	and [resume_flag] is an optional boolean value, 
	which if TRUE, 
		starts the mining process from the last saved alphanumeric value.
	and if FALSE,
		starts the mining process from first alphanumeric value.

2. 	FOR REMOTE IMPLEMENTATION:
	For a worker to connect to a remote master miner, use the following arguments:
	
	sbt "run (ipaddress of the master miner)"
	
3. 	RUNNING UNIT TESTS:
	
	a) 	Open src/main/resources/application.conf
	b) 	Edit the provider field to akka.actor.LocalActorRefProvider
	c) 	sbt test 
	
	Location of the test files:
	src/test/scala-2.11/*.scala
		
Implementation Details:

	1. 	Work Unit:
		The string generation in our project for mining bitcoins uses an iterative approach, with a fixed string length of 32 alphanumeric characters. 
		The total number of permutations that exist for a string of 32 alphanumeric characters is (32)^36.
		
		We decided to opt for an iterative approach of string generation because:
			a) it would give us the option to pause and resume the mining operation,
			b) it will avoid the possiblity of repeated generation of the same string across workers, and
			c) since different workers would be working on mutually exclusive work units, this approach is horizontally scalable. 
		
		The master miner assigns work to the workers in continuous batches of work units, each work unit consisting of 10 million alphanumeric values to mine. 
		A work unit consists of a start and end alphanumeric value, for which the worker should carry out the bitcoin mining process for all the alphanumeric values in that range. 
		
		We decided on a work unit of 10 million alphanumeric values because we wanted each worker to take approximately five to ten minutes to complete each work unit. We decided on a time interval of five-ten minutes because we wanted to reduce the number of message exchanges between the master and workers. 
	
	2.	The result of running your program for
			sbt "run 4"
			
			hsitas444;00000000000000000000000000001lch    00006084d5361b513a2b614300e926d1985f8882421d7596daad47670e87b3c4
			hsitas444;00000000000000000000000000001oba    0000c52e0f42971a2ebf0169e6a5b764f8c13f4a140dbf1e0537b55477631a93
			hsitas444;000000000000000000000000000hx54g    00003d00e77fa3875de06f6a446901c91755593091199937cbeb49298778e58a
			hsitas444;00000000000000000000000000002eub    000070b45afcde439f229175d5a367f8288cebb1d437c93c1cb50e6c10b26c44
			^C

	3. 	The run time of the program on a Intel i7 machine with 2 cores and 4 threads(4 virtual cores with hyper threading) at 2.4 GHz is given below:
		
			time scala project1 5
			
			hsitas444;000000000000000000000000000hy9o4    000006b033396bb01d6aca5cd307b7b10ebe72a47f1a837cc35d3bc57844b140
			hsitas444;000000000000000000000000000647q5    000003bed2103f00ab487a1c341a8cdd5740b729d89b23e2a192c38b757ac9bf
			hsitas444;000000000000000000000000000c8lis    00000b496c586aa8b2c7c0a018c0dd69da031cae96b7afab337a795bb9e41613
			hsitas444;0000000000000000000000000000g39e    00000df54f2332ccc1b1eba805d6625c2171e648517a09cda6dd03310601fe0a
			hsitas444;0000000000000000000000000006korv    00000551896d2f0683481a98bced1453848a7b418e4ec5cb569c6bb80d9ebbae
			^C
			real    1m19.720s
			user    4m45.268s
			sys    0m5.256s
		
		(Total CPU time)/(Number of CPUs) would be same as elapsed real time if the work load is evenly distributed on each CPU and no wait is involved for I/O or other resources.
		Here, Total CPU time = 4m45.268s = 285.268 seconds.
		Real time = 1m19.720s = 79.72 seconds
		
		The ratio of CPU time to REAL TIME = 3.58
		
		
	4.	The coin with the most number of leading 0s that we were able to find:
	
			hsitas444;000000000000000000000000013s83oc    0000000039220951c54b48dc667d5f567aaaa5f77fb81c0fa11392c06b1c61d3

			Number of zeroes: 8
	
	5. 	The largest number of working machines we tested our code on was with 5 machines (4 miners and one master).
	
	6.	Fault Tolerance and Fault Recovery:
		Fault tolerance has been incorporated in our program in the following way: 
		If any worker accepts a batch of work and does not return a result within a specified time interval of one hour, the miner will assign that batch to a new worker.
		Our program handles fault recovery as follows: 
		The current state of the completed alphanumeric values is written into to a text file every 15 minutes. If by chance the master miner crashes or terminated, the mining processed can be resumed from the point where it was last stopped by passing [resume_flag] as TRUE in the program arguments as mentioned under the "Usage" section of this Read-me.

		
	

