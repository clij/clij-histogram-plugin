
for (i = 0; i < 10; i++) {
	run("Close All");
	newImage("Untitled", "16-bit noise", 1024, 1024, 256);

	time = getTime();
	run("Generate histogram on GPU", "cl_device=gfx902 source=Untitled numberofbins=256 minimumgreyvalue=2 maximumgreyvalue=2 determineminandmax persistentmemory");
	IJ.log("Drawing the histogram took " + (getTime() - time) + " msec");
}