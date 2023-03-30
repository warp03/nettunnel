
BINDIR := bin

# https://stackoverflow.com/a/18258352
rwildcard = $(foreach d,$(wildcard $(1:=/*)),$(call rwildcard,$d,$2) $(filter $(subst *,%,$2),$d))

JAVA_CP := omz-common-release.jar:omz-netlib-nio-release.jar
JAVA_CP_OMZPROXY := omz-proxy-release.jar
JAVAC_FLAGS := -Werror -Xlint:all,-processing
JAVA_PATH_SEPARATOR := $(strip $(shell java -XshowSettings:properties 2>&1 | grep path.separator | cut -d '=' -f2))


.PHONY: all
all: nettunnel nettunnel-omzproxy

.PHONY: nettunnel
nettunnel: $(BINDIR)/nettunnel.jar

.PHONY: nettunnel-omzproxy
nettunnel-omzproxy: $(BINDIR)/nettunnel-omzproxy.jar

.PHONY: clean
clean:
	rm -r $(BINDIR)/*

define pre_build
	@mkdir -p $(BINDIR)/$(1)
endef

define post_build
	@[ ! -d $(1)/main/resources ] || cp -r $(1)/main/resources/* $(BINDIR)/$(1)
	jar cf $(BINDIR)/$(1).jar -C $(BINDIR)/$(1) .
endef

$(BINDIR)/nettunnel.jar: $(call rwildcard,nettunnel/main/scala,*.scala)
	$(call pre_build,nettunnel)
	scalac -d $(BINDIR)/nettunnel -cp "$(JAVA_CP)" -explain -deprecation $(filter %.scala,$^)
	$(call post_build,nettunnel)

$(BINDIR)/nettunnel-omzproxy.jar: $(BINDIR)/nettunnel.jar $(call rwildcard,nettunnel-omzproxy/main/scala,*.scala)
	$(call pre_build,nettunnel-omzproxy)
	scalac -d $(BINDIR)/nettunnel-omzproxy -cp "$(JAVA_CP)$(JAVA_PATH_SEPARATOR)$(BINDIR)/nettunnel.jar$(JAVA_PATH_SEPARATOR)$(JAVA_CP_OMZPROXY)" -explain -deprecation $(filter %.scala,$^)
	$(call post_build,nettunnel-omzproxy)
