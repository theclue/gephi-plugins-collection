MAVEN ?= mvn
POM = $(srcdir)/GraphCyclesDetection/pom.xml

all-local:
	$(MAVEN) -f $(POM) package

check-local:
	$(MAVEN) -f $(POM) verify

clean-local:
	$(MAVEN) -f $(POM) clean

EXTRA_DIST = GraphCyclesDetection/pom.xml \
	$(shell cd $(srcdir) && find GraphCyclesDetection/src GraphCyclesDetection/test -type f -print 2>/dev/null) \
	$(shell test ! -f $(srcdir)/README.md || echo README.md)
