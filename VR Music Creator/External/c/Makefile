traceserv-mac: traceserv.c Makefile
	cc -Ofast -o traceserv-mac traceserv.c -I/usr/local/include -L/usr/local/lib -llo

traceserv-linux: traceserv.c Makefile
	clang -O3 -o traceserv-linux traceserv.c -llo -lm

blob_test: blob_test.c Makefile
	cc -Ofast -o blob_test blob_test.c -I/usr/local/include -L/usr/local/lib -llo
