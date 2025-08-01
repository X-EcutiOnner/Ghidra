# Developer's Guide

## Environment
* Primary Language: [Java][java]
* Secondary Languages: [C++][cpp], [Sleigh][sleigh], [Python 3][python] [Jython 2.7][jython]
* Integrated Development Environment: [Eclipse][eclipse]
* Build System: [Gradle][gradle]
* Source Control: [Git][git]

For specific information on required versions and download links please see the 
[README.md](README.md) file.

## Quickstart
Follow the [Advanced Development](README.md#advanced-development) instructions in the [
README.md](README.md) file to get your development environment setup quickly. 

## Licensing and Copyright
* Primary License: [Apache License 2.0][apache]
* Secondary Licenses: [See licenses directory](licenses)

If possible please try to stick to the [Apache License 2.0][apache]
license when developing for Ghidra.  At times it may be necessary to incorporate other compatible 
licenses into Ghidra.  Any GPL code must live in the top-level `GPL/` directory as a totally 
standalone, independently buildable Ghidra module.

If you are contributing code to the Ghidra project, the preferred way to receive credit/recognition 
is Git commit authorship.  Please ensure your Git credentials are properly linked to your GitHub 
account so you appear as a Ghidra contributor on GitHub.  We do not have a standard for putting 
authors' names directly in the source code, so it is discouraged.

## Common Gradle Tasks
Download non-Maven Central dependencies.  This creates a `dependencies` directory in the repository
root.
```
gradle -I gradle/support/fetchDependencies.gradle
```

Download Maven Central dependencies and setup the repository for development.  By default, these 
will be stored at `$HOME/.gradle/`.
```
gradle prepdev
```

Clean up repository build files.  In rare circumstances this may be necessary after a `git pull` to
fix unexplainable compilation errors.
```
gradle clean
```

Generate nested Eclipse project files which can then be imported into Eclipse as "existing 
projects".
```
gradle cleanEclipse eclipse
```

Build native components for your current platform.  Requires native tool chains to be present.
```
gradle buildNatives
```

Manually compile sleigh files. Ghidra will also do this at runtime when necessary.
```
gradle sleighCompile
```

Build Javadoc:
```
gradle createJavadocs
```

Build Python3 packages for PyGhidra and the Debugger:
```
gradle buildPyPackage
```

Build Ghidra to `build/dist` in an uncompressed form.  This will be a distribution intended only to 
run on the platform on which it was built.
```
gradle assembleAll
```

Build Ghidra to `build/dist` in a compressed form.  This will be a distribution intended only to run
on the platform on which it was built.
```
gradle buildGhidra
```

**Tip:**  You may want to skip certain Gradle tasks to speed up your build, or to deal with
a problem later.  For example, perhaps you added some new source files and the build is failing 
because of unresolved IP header issues.  You can use the Gradle `-x <task>` command line argument to
prevent specific tasks from running:
```
gradle buildGhidra -x ip
```

## PyGhidra Development
The supported way to develop and debug PyGhidra is with the _[PyDev][pydev]_ plugin for Eclipse.
When PyDev is installed and configured, several new Eclipse run configurations will appear that
enable running and debugging PyGhidra from both _GUI_ and _Interpreter_ modes.

To prepare PyGhidra for development and/or debugging, first execute the following gradle task:
```
gradle prepPyGhidra
```
This sets up a Python virtual environment at `build/venv/`, and installs an editable PyGhidra
module (and its dependencies) into it. PyDev should be pointed at this virtual environment so it has
access to the editable PyGhidra module, as well as the typing/stub information. From Eclipse 
(with PyDev installed):

1. _Settings -> PyDev -> Interpreters -> Python Interpreter_
2. Click _New..._
3. Click _Browse for python/pypy exe_
4. Choose `build/venv/bin/python3`
5. Enter a value for _Interpreter Name_
6. Check _Select All_ and press _OK_
7. Click the _Prefined_ tab, and then _New..._
8. Choose `build/typestubs/pypredef`
9. Click _Apply and Close_

## GhidraDev Eclipse Plugin Development
Developing the GhidraDev Eclipse plugin requires the 
_Eclipse PDE (Plug-in Development Environment)_, which can be installed via the Eclipse marketplace.
It is also included in the _Eclipse IDE for RCP and RAP Developers_. To generate the GhidraDev 
Eclipse projects and prepare the necessary dependencies, execute:

```
gradle prepGhidraDev eclipse -PeclipsePDE
```

Import the newly generated GhidraDev projects into an Eclipse that supports this type of project. 

__Note:__ If you are getting compilation errors related to PyDev and CDT, go into Eclipse's 
preferences, and under _Target Platform_, activate _/Eclipse GhidraDevPlugin/GhidraDev.target_.

See [Building GhidraDev](GhidraBuild/EclipsePlugins/GhidraDev/GhidraDevPlugin/README.md#building)
for instructions on how to build the GhidraDev plugin.

## Offline Development Environment
Sometimes you may want to move the Ghidra repository to an offline network and do development there.
These are the recommended steps to ensure that you not only move the source repository, but all 
downloaded dependencies as well:

1. `gradle -I gradle/support/fetchDependencies.gradle`
2. `gradle -g dependencies/gradle prepdev`
3. Move ghidra directory to different system
4. `gradle -g dependencies/gradle buildGhidra` (on offline system)

**NOTE**: The `-g` flag specifies the Gradle user home directory. The default is the `.gradle`
directory in the user’s home directory.  Overriding it to be inside the Ghidra repository will
ensure that all maven central dependencies that were fetched during the `prepdev` task will be moved
along with the rest of the repo.

## Running tests
To run unit tests, do:
```
gradle unitTestReport
```

For more complex integration tests, do:
```
gradle integrationTest
```

For running both unit and integration tests and to generate a report do:
```
gradle combinedTestReport
```

## Setup build in CI

For running tests in headless mode on Linux, in a CI environment, or in Docker, first do:
```
Xvfb :99 -nolisten tcp &
export DISPLAY=:99
```
This is required to make AWT happy.

## Building Supporting Data

Some features of Ghidra require the curation of rather extensive databases. These include the Data 
Type Archives and Function ID Databases, both of which require collecting header files and libraries
for the relevant SDKs and platforms. Much of this work is done by hand. The archives included in our
official builds can be found in the [ghidra-data] repository.

### Building Data Type Archives

This task is often done manually from the Ghidra GUI, and the archives included in our official 
build require a fair bit of fine tuning.
1. From the CodeBrowser, select __File -> Parse C Source__
2. From here you can create and configure
parsing profiles, which lists headers and pre-processor options.
3. Click _Parse to File_ to create the Data Type Archive.
4. The result can be added to an installation or source tree by copying it to 
`Ghidra/Features/Base/data/typeinfo`.

### Building FID Databases

This task is often done manually from the Ghidra GUI, and the archives included in our official 
build require a fair bit of fine tuning. You will first need to import the relevant libraries from 
which you'd like to produce a FID database. This is often a set of libraries from an SDK. We include
a variety of Visual Studio platforms in the official build. The official .fidb files can be found in
the [ghidra-data][ghidra-data] repository.

1. From the CodeBrowser, select __File -> Configure__
2. Enable the "Function ID" plugins, and close the dialog.
3. From the CodeBrowser, select __Tools -> Function ID -> Create new empty FidDb__.
4. Choose a destination file.
5. Select __Tools -> Function ID -> Populate FidDb__ from programs.
6. Fill out the options appropriately and click OK.

If you'd like some details of our fine tuning, take a look at [building_fid.txt](Ghidra/Features/FunctionID/data/building_fid.txt).

## Debugger Development

We have recently changed the Debugger's back-end architecture.
We no longer use JNA to access native Debugger APIs.
We only use it for pseudo-terminal access.
Instead, we use Python3 and a protobuf-based TCP connection for back-end integration.

### Additional Dependencies

In addition to Ghidra's normal dependencies, you may want the following:

 * WinDbg for Windows x64
 * GDB 13 or later for Linux
 * LLDB 10 or later for macOS

The others (e.g., JNA) are handled by Gradle via Maven Central.

### Architecture Overview

There are several Eclipse projects each fitting into a larger architectural picture.
These all currently reside in the `Ghidra/Debug` directory, but will likely be re-factored into the
`Framework` and `Feature` directories later. Each project is listed "bottom up" with a brief 
description and status.

 * ProposedUtils - a collection of utilities proposed to be moved to other respective projects.
 * AnnotationValidator - an experimental annotation processor for database access objects.
 * Framework-TraceModeling - a database schema and set of interfaces for storing machine state over
 time.
 * Framework-AsyncComm - a collection of utilities for asynchronous communication (packet formats
 and completable-future conveniences).
 * Debugger-api - the interfaces for interacting with the Debugger UI.
 * Debugger - the collection of Ghidra plugins and services comprising the Debugger UI implementation.
 * Debugger-isf - A service providing access to Ghidra's DataTypes via ISF.
 * Debugger-rmi-trace - the wire protocol, client, services, and UI components for Trace RMI, the new back-end architecture.
 * Debugger-agent-dbgeng - the connector for WinDbg (via dbgeng.dll and dbgmodel.dll) on Windows x64.
 * Debugger-agent-gdb - the connector for GDB (13 or later recommended) on UNIX and Windows.
 * Debugger-agent-lldb - the connector for LLDB (10 or later recommended) on macOS, UNIX, and Windows.
 * Debugger-jpda - an in-development connector for Java and Dalvik debugging via JDI (i.e., JDWP). This is deprecated and not yet replaced.

The Trace Modeling schema records machine state and markup over time.
It rests on the same database framework as Programs, allowing trace recordings to be stored in a Ghidra project and shared via a server, if desired.
Trace "recording" is a de facto requirement for displaying information in Ghidra's UI.
The back-end connector has full discretion over what is recorded by using Trace RMI.
Typically, only the machine state actually observed by the user (or perhaps a script) is recorded.
For most use cases, the Trace is small and ephemeral, serving only to mediate between the UI components and the target's model.
It supports many of the same markup (e.g., disassembly, data types) as Programs, in addition to tracking active threads, loaded modules, breakpoints, etc.

Every back end (or "adapter" or "connector" or "agent") employs the Trace RMI client to populate a trace database.
As a general rule in Ghidra, no component is allowed to access a native API and reside in the same JVM as the Ghidra UI.
This allows us to contain crashes, preventing data loss.
To accommodate this requirement &mdash; given that debugging native applications is almost certainly going to require access to native APIs &mdash; we've developed the Trace RMI protocol.
This also allows us to better bridge the language gap between Java and Python, which is supported by most native debuggers.
This protocol is loosely coupled to Framework-TraceModeling, essentially exposing its methods via RMI, as well as some methods for controlling the UI.
The protocol is built using Google's Protobuf library, providing a potential path for back-end implementations in alternative languages.
We provide the Trace RMI server as a Ghidra component implemented in Java and the Trace RMI client as a Python3 package.
The client is also available in Java, but it depends heavily on Ghidra's code base.
A back-end implementation may be a stand-alone executable or script that accesses the native debugger's API, or a script or plugin for the native debugger.
It then connects to Ghidra via Trace RMI to populate the trace database with information gleaned from that API.
It should provide a set of diagnostic commands to control and monitor that connection.
It should also use the native API to detect session and target changes so that Ghidra's UI consistently reflects the debugging session.
It is the back-end's responsibility to discover targets in the session and map them to traces in the proper Ghidra language.
Typically, it examines the target's architecture and immediately creates a trace upon connection.

### Developing a new connector

So Ghidra does not yet support your favorite debugger?
We believe the new system is much less daunting than the previous.
Still, please finish reading this guide, and look carefully at the ones we have so far, and perhaps ask to see if we are already developing one.
Of course, in time you might also search the internet to see if others are developing one.
There are quite a few caveats and gotchas, the most notable being that this interface is still in some flux.
When things go wrong, it could be because of, without limitation:

1. A bug on your part
2. A bug on our part
3. A design flaw in the interfaces
4. A bug in the debugger/API you're adapting

We are still (yes, still) in the process of writing up this documentation.
In the meantime, we recommend using the GDB and dbgeng agents as examples.
Be sure to look at the Python code `src/main/py`!
This is not so readily presented by Eclipse.

You'll also need to provide launcher(s) so that Ghidra knows how to configure and start your connector.
These are just shell scripts.
We use bash scripts on Linux and macOS, and we use batch files on Windows.
The ideal goal for a launcher is (after one-time configuration) the user can launch and begin debugging with a single click.
Try to include as many common use cases as makes sense for the debugger.
This provides the most flexibility to users and examples to power users who might create derivative launchers.
Look at the existing launchers for examples.

For testing, please follow the examples for GDB.
We no longer provide abstract classes that prescribe requirements.
Instead, we just provide GDB as an example or template.
Usually, we split our tests into three categories:

 * Commands
 * Methods
 * Hooks

The Commands tests check that the user CLI commands, conventionally implemented in `commands.py`, work correctly.
In general, do the minimum connection setup, execute the command, and check that it produces the expected output and causes the expected effects.

The Methods tests check that the remote methods, conventionally implemented in `methods.py`, work correctly.
Many methods are just wrappers around CLI commands, some provided by the native debugger and some provided by `commands.py`.
These work similarly to the commands test, except that they invoke methods instead of executing commands.
Check the return value (rarely applicable) and that it causes the expected effects.

The Hooks tests check that the back end is able to listen for session and target changes, e.g., knowing when the target stops.
*The test should not "cheat" by executing commands or invoking methods that should instead be triggered by the hook.*
It should execute the minimal commands to setup the test, then trigger an event.
It should then check that the event in turn triggered the expected effects, e.g., updating PC upon the target stopping.

Whenever you make a change to the Python code, you'll need to re-assemble the package's source.

```
gradle assemblePyPackage
```

This is required in case your package includes generated source, as is the case for Debugger-rmi-trace.
If you want to create a new Ghidra module for your connector (recommended) use an existing one's `build.gradle` as a template.
A key part is applying the `hasPythonPackage.gradle` script.

### Adding a new platform

If a connector already exists for a suitable debugger on the desired platform, then adding it may be very simple.
For example, many platforms are supported by GDB, so even though we're currently focused on x86-64 (and to some extent arm64) support, we've provided the mappings for many.
These mappings are conventionally kept in each connector's `arch.py` file.

In general, to update `arch.py`, you need to know:

1. What the platform is called (including variant names) by the debugger
2. What the processor language is called by Ghidra
3. If applicable, the mapping of target address spaces into Ghidra's address spaces
4. If applicable, the mapping of target register names to those in Ghidra's processor language

In most cases (3) and (4) are already implemented by the included mappers.
Naturally, you'll want to test the special cases, preferably in automated tests.

### Emulation

The most obvious integration path for 3rd-party emulators is to write a "connector."
However, p-code emulation is an integral feature of the Ghidra UI, and it has a fairly accessible API.
Namely, for interpolation between machines states recorded in a trace, and extrapolation into future machine states.
Integration of such emulators may still be useful to you, but we recommend trying the p-code emulator to see if it suits your needs for emulation in Ghidra before pursuing integration of another emulator.
We also provide out-of-the-box QEMU integration via GDB.

### Contributing

When submitting help tickets and pull requests, please tag those related to the debugger with "Debugger" so that we can triage them more quickly.

## Troubleshooting and Help

### Eclipse Issues
After pulling or syncing with the latest Ghidra source repository, you might run into the following
issues in Eclipse:

* __Problem:__ _There are Eclipse compilation errors that I don't know how to deal with...I give up!_
  * __Solution:__
    * From Eclipse, collapse all projects in the _Package Explorer_ or _Project Explorer_ by
      clicking the `⊟` icon in that frame
    * Locate any projects in the _Package Explorer_ or _Project Explorer_ that have little `?` icons
      on them (these projects should no longer be in source control)
    * Right-click on __only them__, and then click _Delete_.
    * __CHECK__ the _"Delete project contents on disk"_ checkbox.
    * Click _OK_ (confirm git does not contain any new unstaged files for delete)
    * Select all projects in the _Package Explorer_ or _Project Explorer_
    * Right-click on them, and then click _Delete_ (this may not work if projects are not collapsed)
    * Leave _"Delete project contents on disk"_ checkbox __UNCHECKED__
    * Click _OK_.  You should now have an empty _Package Explorer_ or _Project Explorer_.
    * `gradle -I gradle/support/fetchDependencies.gradle`
    * `gradle prepdev cleanEclipse eclipse buildNatives`
    * From Eclipse, _File -> Import..._
    * _General | Existing Projects into Workspace_
    * Select root directory to be your downloaded or cloned ghidra source repository
    * Check _"Search for nested projects"_
    * Click _Finish_
    
    This should get Eclipse back to a fresh state. There should never be a need to re-clone the
    repository.

* __Problem:__ _The Ghidra run configurations (launchers) are missing_.
  * __Solution:__
    The Ghidra run configurations are kept under source control in various modules' `.launch/` 
    directories (i.e., `Ghidra/Features/Base/.launch/`). As long as the corresponding module 
    project is imported into Eclipse (i.e., `Features Base`), the run configurations should be
    available in Eclipse under _Run -> Run Configurations_.  If they aren't there and the 
    projects are imported, try closing and reopening Eclipse. 
    
    __NOTE:__ Sometimes you have to launch Ghidra via the _Run -> Run Configurations..._ window one
    time for the run configuration to show up under the favorites menu in the main Eclipse button 
    bar.
    
    __NOTE:__ Never address missing run configurations by manually importing them via _File -> 
    Import... -> Run/Debug -> Launch Configurations._ This avoids the real issue and will 
    inevitably result in duplicate run configurations showing up one day, which can cause
    additional confusion.

## Known Issues
* There is a known issue in Gradle that can prevent it from discovering native toolchains on Linux 
  if a non-English system locale is being used. As a workaround, set the following environment 
  variable prior to running your Gradle task: `LC_MESSAGES=en_US.UTF-8`
* If the Ghidra build is only finding versions of Python that do not have access to `pip`, it may
  be necessary to perform the build from a Python [virtual environment][venv].

[java]: https://dev.java
[cpp]: https://isocpp.org
[sleigh]: https://htmlpreview.github.io/?https://github.com/NationalSecurityAgency/ghidra/blob/stable/GhidraDocs/languages/index.html
[python]: https://www.python.org
[venv]: https://docs.python.org/3/tutorial/venv.html
[jython]: https://www.jython.org
[eclipse]: https://www.eclipse.org/downloads/
[pydev]: https://www.pydev.org
[gradle]: https://gradle.org
[git]: https://git-scm.com
[apache]: https://www.apache.org/licenses/LICENSE-2.0
[fork]: https://docs.github.com/en/get-started/quickstart/fork-a-repo
[ghidra-data]: https://github.com/NationalSecurityAgency/ghidra-data
[DbgGuide]: DebuggerDevGuide.md
