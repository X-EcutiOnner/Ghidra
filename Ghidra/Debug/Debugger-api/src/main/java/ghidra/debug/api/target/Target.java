/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.debug.api.target;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import javax.swing.Icon;

import docking.ActionContext;
import ghidra.debug.api.target.ActionName.Show;
import ghidra.debug.api.tracemgr.DebuggerCoordinates;
import ghidra.program.model.address.*;
import ghidra.program.model.lang.Register;
import ghidra.program.model.lang.RegisterValue;
import ghidra.trace.model.Trace;
import ghidra.trace.model.TraceExecutionState;
import ghidra.trace.model.breakpoint.*;
import ghidra.trace.model.guest.TracePlatform;
import ghidra.trace.model.memory.TraceMemoryState;
import ghidra.trace.model.stack.TraceStackFrame;
import ghidra.trace.model.target.TraceObject;
import ghidra.trace.model.target.path.KeyPath;
import ghidra.trace.model.thread.TraceThread;
import ghidra.trace.model.time.TraceSnapshot;
import ghidra.trace.model.time.schedule.TraceSchedule;
import ghidra.trace.model.time.schedule.TraceSchedule.ScheduleForm;
import ghidra.util.Swing;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;

/**
 * The interface between the front-end UI and the back-end connector.
 * 
 * <p>
 * Anything the UI might command a target to do must be defined as a method here. Each
 * implementation can then sort out, using context from the UI as appropriate, how best to effect
 * the command using the protocol and resources available on the back-end.
 */
public interface Target {
	long TIMEOUT_MILLIS = 10000;

	/**
	 * A description of a UI action provided by this target.
	 * 
	 * <p>
	 * In most cases, this will generate a menu entry or a toolbar button, but in some cases, it's
	 * just invoked implicitly. Often, the two suppliers are implemented using lambda functions, and
	 * those functions will keep whatever some means of querying UI and/or target context in their
	 * closures.
	 */
	interface ActionEntry {

		/**
		 * Get the text to display on UI actions associated with this entry
		 * 
		 * @return the display
		 */
		String display();

		/**
		 * Get the name of a common debugger command this action implements
		 * 
		 * @return the name
		 */
		ActionName name();

		/**
		 * Get the icon to display in menus and dialogs
		 * 
		 * @return the icon
		 */
		Icon icon();

		/**
		 * Get the text providing more details, usually displayed in a tool tip
		 * 
		 * @return the details
		 */
		String details();

		/**
		 * Check whether invoking the action requires further user interaction
		 * 
		 * @return true if prompting is required
		 */
		boolean requiresPrompt();

		/**
		 * Get a relative score of specificity.
		 * 
		 * <p>
		 * These are only meaningful when compared among entries returned in the same collection.
		 * 
		 * @return the specificity
		 */
		long specificity();

		/**
		 * Invoke the action asynchronously, prompting if desired.
		 * 
		 * <p>
		 * The implementation is not required to provide a timeout; however, downstream components
		 * may.
		 * 
		 * @param prompt whether or not to prompt the user for arguments
		 * @return the future result, often {@link Void}
		 */
		CompletableFuture<?> invokeAsyncWithoutTimeout(boolean prompt);

		/**
		 * Check if this action is currently enabled
		 * 
		 * @return true if enabled
		 */
		boolean isEnabled();

		/**
		 * Invoke the action asynchronously, prompting if desired
		 * 
		 * <p>
		 * Note this will impose a timeout of {@value Target#TIMEOUT_MILLIS} milliseconds.
		 * 
		 * @param prompt whether or not to prompt the user for arguments
		 * @return the future result, often {@link Void}
		 */
		default CompletableFuture<?> invokeAsync(boolean prompt) {
			return invokeAsyncWithoutTimeout(prompt).orTimeout(TIMEOUT_MILLIS,
				TimeUnit.MILLISECONDS);
		}

		/**
		 * Invoke the action synchronously
		 * 
		 * <p>
		 * To avoid blocking the Swing thread on a remote socket, this method cannot be called on
		 * the Swing thread.
		 * 
		 * @param prompt whether or not to prompt the user for arguments
		 */
		default void run(boolean prompt) {
			get(prompt);
		}

		/**
		 * Invoke the action synchronously, getting its result
		 * 
		 * @param prompt whether or not to prompt the user for arguments
		 * @return the resulting value, if applicable
		 */
		default Object get(boolean prompt) {
			if (Swing.isSwingThread()) {
				throw new AssertionError("Refusing to block the Swing thread. Use a Task.");
			}
			try {
				return invokeAsync(prompt).get();
			}
			catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		}

		/**
		 * Check if this action's name is built in
		 * 
		 * @return true if built in.
		 */
		default Show getShow() {
			return name() == null ? Show.EXTENDED : name().show();
		}
	}

	/**
	 * Specifies how object arguments are derived
	 */
	public enum ObjectArgumentPolicy {
		/**
		 * The object should be taken exactly from the action context, if applicable, present, and
		 * matching in schema.
		 */
		CONTEXT_ONLY {
			@Override
			public boolean allowContextObject() {
				return true;
			}

			@Override
			public boolean allowCoordsObject() {
				return false;
			}

			@Override
			public boolean allowSuitableRelative() {
				return false;
			}
		},
		/**
		 * The object should be taken from the current (active) object in the tool, or a suitable
		 * relative having the correct schema.
		 */
		CURRENT_AND_RELATED {
			@Override
			public boolean allowContextObject() {
				return false;
			}

			@Override
			public boolean allowCoordsObject() {
				return true;
			}

			@Override
			public boolean allowSuitableRelative() {
				return true;
			}
		},
		/**
		 * The object can be taken from the given context, or the current (active) object in the
		 * tool, or a suitable relative having the correct schema.
		 */
		EITHER_AND_RELATED {
			@Override
			public boolean allowContextObject() {
				return true;
			}

			@Override
			public boolean allowCoordsObject() {
				return true;
			}

			@Override
			public boolean allowSuitableRelative() {
				return true;
			}
		};

		public abstract boolean allowContextObject();

		public abstract boolean allowCoordsObject();

		public abstract boolean allowSuitableRelative();
	}

	/**
	 * Describe the target for display in the UI
	 * 
	 * @return the description
	 */
	String describe();

	/**
	 * Check if the target is still valid
	 * 
	 * @return true if valid
	 */
	boolean isValid();

	/**
	 * Get the trace into which this target is recorded
	 * 
	 * @return the trace
	 */
	Trace getTrace();

	/**
	 * Get the current snapshot key for the target
	 * 
	 * <p>
	 * For most targets, this is the most recently created snapshot. For time-traveling targets, if
	 * may not be. If this returns a negative number, then it refers to a scratch snapshot and
	 * almost certainly indicates time travel with instruction steps. Use {@link #getTime()} in that
	 * case to get a more precise schedule.
	 * 
	 * @return the snapshot
	 */
	long getSnap();

	/**
	 * Get the current time
	 * 
	 * @return the current time
	 */
	default TraceSchedule getTime() {
		long snap = getSnap();
		if (snap >= 0) {
			return TraceSchedule.snap(snap);
		}
		TraceSnapshot snapshot = getTrace().getTimeManager().getSnapshot(snap, false);
		if (snapshot == null) {
			return null;
		}
		return snapshot.getSchedule();
	}

	/**
	 * Get the form of schedules supported by "activate" on the back end
	 * 
	 * <p>
	 * A non-null return value indicates the back end supports time travel. If it does, the return
	 * value indicates the form of schedules that can be activated, (i.e., via some "go to time"
	 * command). NOTE: Switching threads is considered an event by every time-traveling back end
	 * that we know of. Events are usually mapped to a Ghidra trace's snapshots, and so most back
	 * ends are constrained to schedules of the form {@link ScheduleForm#SNAP_EVT_STEPS}. A back-end
	 * based on emulation may support thread switching. To support p-code op stepping, the back-end
	 * will certainly have to be based on p-code emulation, and it must be using the same Sleigh
	 * language as Ghidra.
	 * 
	 * @param obj the object (or an ancestor) that may support time travel
	 * @param snap the <em>destination</em> snapshot
	 * @return the form
	 */
	public ScheduleForm getSupportedTimeForm(TraceObject obj, long snap);

	/**
	 * Collect all actions that implement the given common debugger command
	 * 
	 * <p>
	 * Note that if the context provides a program location (i.e., address), the object policy is
	 * ignored. It will use current and related objects.
	 * 
	 * @param name the action name
	 * @param context applicable context from the UI
	 * @param policy determines how objects may be found
	 * @return the collected actions
	 */
	Map<String, ActionEntry> collectActions(ActionName name, ActionContext context,
			ObjectArgumentPolicy policy);

	/**
	 * @see #execute(String, boolean)
	 */
	CompletableFuture<String> executeAsync(String command, boolean toString);

	/**
	 * Execute a command as if in the CLI
	 * 
	 * @param command the command
	 * @param toString true to capture the output and return it, false to print to the terminal
	 * @return the captured output, or null if {@code toString} is false
	 */
	String execute(String command, boolean toString);

	/**
	 * Get the trace thread that contains the given object
	 * 
	 * @param path the path of the object
	 * @return the thread, or null
	 */
	TraceThread getThreadForSuccessor(KeyPath path);

	/**
	 * Get the execution state of the given thread
	 * 
	 * @param thread the thread
	 * @return the state
	 */
	TraceExecutionState getThreadExecutionState(TraceThread thread);

	/**
	 * Get the trace stack frame that contains the given object
	 * 
	 * @param path the path of the object
	 * @return the stack frame, or null
	 */
	TraceStackFrame getStackFrameForSuccessor(KeyPath path);

	/**
	 * Check if the target supports synchronizing focus
	 * 
	 * @return true if supported
	 */
	boolean isSupportsFocus();

	/**
	 * Get the object that currently has focus on the back end's UI
	 * 
	 * @return the focused object's path, or null
	 */
	KeyPath getFocus();

	/**
	 * @see #activate(DebuggerCoordinates, DebuggerCoordinates)
	 */
	CompletableFuture<Void> activateAsync(DebuggerCoordinates prev, DebuggerCoordinates coords);

	/**
	 * Request that the back end's focus be set to the same as the front end's (Ghidra's) GUI.
	 * 
	 * @param prev the GUI's immediately previous coordinates
	 * @param coords the GUI's current coordinates
	 */
	void activate(DebuggerCoordinates prev, DebuggerCoordinates coords);

	/**
	 * @see #invalidateMemoryCaches()
	 */
	CompletableFuture<Void> invalidateMemoryCachesAsync();

	/**
	 * Invalidate any caches on the target's back end or on the client side of the connection.
	 * 
	 * <p>
	 * In general, back ends should avoid doing any caching. Instead, the front-end will assume
	 * anything marked {@link TraceMemoryState#KNOWN} is up to date. I.e., the trace database acts
	 * as the client-side cache for a live target.
	 * 
	 * <p>
	 * <b>NOTE:</b> This method exists for invalidating model-based target caches. It may be
	 * deprecated and removed, unless it turns out we need this for Trace RMI, too.
	 */
	void invalidateMemoryCaches();

	/**
	 * @see #readMemory(AddressSetView, TaskMonitor)
	 */
	CompletableFuture<Void> readMemoryAsync(AddressSetView set, TaskMonitor monitor);

	/**
	 * Read and capture several ranges of target memory
	 * 
	 * <p>
	 * The target may read more than the requested memory, usually because it will read all pages
	 * containing any portion of the requested set. The target should attempt to read at least the
	 * given memory. To the extent it is successful, it must cause the values to be recorded into
	 * the trace <em>before</em> this method returns. Only if the request is <em>entirely</em>
	 * unsuccessful should this method throw an exception. Otherwise, the failed portions, if any,
	 * should be logged without throwing an exception.
	 * 
	 * @param set the addresses to capture
	 * @param monitor a monitor for displaying task steps
	 * @throws CancelledException if the operation is cancelled
	 */
	void readMemory(AddressSetView set, TaskMonitor monitor) throws CancelledException;

	/**
	 * @see #readMemory(AddressSetView, TaskMonitor)
	 */
	CompletableFuture<Void> writeMemoryAsync(Address address, byte[] data);

	/**
	 * Write data to the target's memory
	 * 
	 * <p>
	 * The target should attempt to write the memory. To the extent it is successful, it must cause
	 * the effects to be recorded into the trace <em>before</em> this method returns. Only if the
	 * request is <em>entirely</em> unsuccessful should this method throw an exception. Otherwise,
	 * the failed portions, if any, should be logged without throwing an exception.
	 * 
	 * @param address the starting address
	 * @param data the bytes to write
	 */
	void writeMemory(Address address, byte[] data);

	/**
	 * @see #readRegisters(TracePlatform, TraceThread, int, Set)
	 */
	CompletableFuture<Void> readRegistersAsync(TracePlatform platform, TraceThread thread,
			int frame, Set<Register> registers);

	/**
	 * Read and capture the named target registers for the given platform, thread, and frame.
	 * 
	 * <p>
	 * Target target should read the registers and, to the extent it is successful, cause the values
	 * to be recorded into the trace <em>before</em> this method returns. Only if the request is
	 * <em>entirely</em> unsuccessful should this method throw an exception. Otherwise, the failed
	 * registers, if any, should be logged without throwing an exception.
	 * 
	 * @param platform the platform defining the registers
	 * @param thread the thread whose context contains the register values
	 * @param frame the frame, if applicable, for saved register values. 0 for current values.
	 * @param registers the registers to read
	 */
	void readRegisters(TracePlatform platform, TraceThread thread, int frame,
			Set<Register> registers);

	/**
	 * @see #readRegistersAsync(TracePlatform, TraceThread, int, AddressSetView)
	 */
	CompletableFuture<Void> readRegistersAsync(TracePlatform platform, TraceThread thread,
			int frame, AddressSetView guestSet);

	/**
	 * Read and capture the target registers in the given address set.
	 * 
	 * <p>
	 * Aside from how registers are named, this works equivalently to
	 * {@link #readRegisters(TracePlatform, TraceThread, int, Set)}.
	 */
	void readRegisters(TracePlatform platform, TraceThread thread, int frame,
			AddressSetView guestSet);

	/**
	 * @see #writeRegister(TracePlatform, TraceThread, int, RegisterValue)
	 */
	CompletableFuture<Void> writeRegisterAsync(TracePlatform platform, TraceThread thread,
			int frame, RegisterValue value);

	/**
	 * Write a value to a target register for the given platform, thread, and frame
	 * 
	 * <p>
	 * The target should attempt to write the register. If successful, it must cause the effects to
	 * be recorded into the trace <em>before</em> this method returns. If the request is
	 * unsuccessful, this method throw an exception.
	 * 
	 * @param address the starting address
	 * @param data the bytes to write
	 */
	void writeRegister(TracePlatform platform, TraceThread thread, int frame, RegisterValue value);

	/**
	 * @see #writeRegister(TracePlatform, TraceThread, int, Address, byte[])
	 */
	CompletableFuture<Void> writeRegisterAsync(TracePlatform platform, TraceThread thread,
			int frame, Address address, byte[] data);

	/**
	 * Write a value to a target register by its address
	 * 
	 * <p>
	 * Aside from how the register is named, this works equivalently to
	 * {@link #writeRegister(TracePlatform, TraceThread, int, RegisterValue)}. The address is the
	 * one defined by Ghidra.
	 */
	void writeRegister(TracePlatform platform, TraceThread thread, int frame, Address address,
			byte[] data);

	/**
	 * Check if a given variable (register or memory) exists on target
	 * 
	 * @param platform the platform whose language defines the registers
	 * @param thread if a register, the thread whose registers to examine
	 * @param frame the frame level, usually 0.
	 * @param address the address of the variable
	 * @param size the size of the variable. Ignored for memory
	 * @return true if the variable can be mapped to the target
	 */
	boolean isVariableExists(TracePlatform platform, TraceThread thread, int frame, Address address,
			int length);

	/**
	 * @see #writeVariable(TracePlatform, TraceThread, int, Address, byte[])
	 */
	CompletableFuture<Void> writeVariableAsync(TracePlatform platform, TraceThread thread,
			int frame,
			Address address, byte[] data);

	/**
	 * Write a variable (memory or register) of the given thread or the process
	 * 
	 * <p>
	 * This is a convenience for writing target memory or registers, based on address. If the given
	 * address represents a register, this will attempt to map it to a register and write it in the
	 * given thread and frame. If the address is in memory, it will simply delegate to
	 * {@link #writeMemory(Address, byte[])}.
	 * 
	 * @param thread the thread. Ignored (may be null) if address is in memory
	 * @param frameLevel the frame, usually 0. Ignored if address is in memory
	 * @param address the starting address
	 * @param data the value to write
	 */
	void writeVariable(TracePlatform platform, TraceThread thread, int frame, Address address,
			byte[] data);

	/**
	 * Get the kinds of breakpoints supported by the target.
	 * 
	 * @return the set of kinds
	 */
	Set<TraceBreakpointKind> getSupportedBreakpointKinds();

	/**
	 * @see #placeBreakpoint(AddressRange, Set, String, String)
	 */
	CompletableFuture<Void> placeBreakpointAsync(AddressRange range,
			Set<TraceBreakpointKind> kinds, String condition, String commands);

	/**
	 * Place a new breakpoint of the given kind(s) over the given range
	 * 
	 * <p>
	 * If successful, this method must cause the breakpoint to be recorded into the trace.
	 * Otherwise, it should throw an exception.
	 * 
	 * @param range the range. NOTE: The target is only required to support length-1 execution
	 *            breakpoints.
	 * @param kinds the kind(s) of the breakpoint.
	 * @param condition optionally, a condition for the breakpoint, expressed in the back-end's
	 *            language. NOTE: May be silently ignored by the implementation, if not supported.
	 * @param commands optionally, a command to execute upon hitting the breakpoint, expressed in
	 *            the back-end's language. NOTE: May be silently ignored by the implementation, if
	 *            not supported.
	 */
	void placeBreakpoint(AddressRange range, Set<TraceBreakpointKind> kinds, String condition,
			String commands);

	/**
	 * Check if the given breakpoint (location) is still valid on target
	 * 
	 * @param breakpoint the breakpoint
	 * @return true if valid
	 */
	boolean isBreakpointValid(TraceBreakpointLocation breakpoint);

	/**
	 * @see #deleteBreakpoint(TraceBreakpointCommon)
	 */
	CompletableFuture<Void> deleteBreakpointAsync(TraceBreakpointCommon breakpoint);

	/**
	 * Delete the given breakpoint from the target
	 * 
	 * <p>
	 * If successful, this method must cause the breakpoint removal to be recorded in the trace.
	 * Otherwise, it should throw an exception.
	 * 
	 * @param breakpoint the breakpoint to delete
	 */
	void deleteBreakpoint(TraceBreakpointCommon breakpoint);

	/**
	 * @see #toggleBreakpoint(TraceBreakpointLocation, boolean)
	 */
	CompletableFuture<Void> toggleBreakpointAsync(TraceBreakpointCommon breakpoint,
			boolean enabled);

	/**
	 * Toggle the given breakpoint on the target
	 * 
	 * <p>
	 * If successful, this method must cause the breakpoint toggle to be recorded in the trace. If
	 * the state is already as desired, this method may have no effect. If unsuccessful, this method
	 * should throw an exception.
	 * 
	 * @param breakpoint the breakpoint to toggle
	 * @param enabled true to enable, false to disable
	 */
	void toggleBreakpoint(TraceBreakpointCommon breakpoint, boolean enabled);

	/**
	 * @see #forceTerminate()
	 */
	CompletableFuture<Void> forceTerminateAsync();

	/**
	 * Forcefully terminate the target
	 * 
	 * <p>
	 * This will first attempt to kill the target gracefully. In addition, and whether or not the
	 * target is successfully terminated, the target will be dissociated from its trace, and the
	 * target will be invalidated. To attempt only a graceful termination, check
	 * {@link #collectActions(ActionName, ActionContext)} with {@link ActionName#KILL}.
	 */
	void forceTerminate();

	/**
	 * @see #disconnect()
	 */
	CompletableFuture<Void> disconnectAsync();

	/**
	 * Terminate the target and its connection
	 * 
	 * <p>
	 * <b>WARNING:</b> This terminates the connection, even if there are other live targets still
	 * using it. One example where this might happen is if the target process launches a child, and
	 * the debugger is configured to remain attached to both. Whether this is expected or acceptable
	 * behavior has not been decided.
	 * 
	 * <p>
	 * <b>NOTE:</b> This method cannot be invoked on the Swing thread, because it may block on I/O.
	 * 
	 * @see #disconnectAsync()
	 */
	void disconnect();

	/**
	 * Check if the target is busy updating the trace
	 * 
	 * <p>
	 * This generally means the connection has an open transaction. If <em>does not</em> indicate
	 * the execution state of the target/debuggee.
	 * 
	 * @return true if busy
	 */
	boolean isBusy();

	/**
	 * Forcibly commit all of the back-ends transactions on this target's trace.
	 * 
	 * <p>
	 * This is generally not a recommended course of action, except that sometimes the back-end
	 * crashes and fails to close a transaction. It should only be invoked by a relatively hidden
	 * menu option, and mediated by a warning of some sort. Closing a transaction prematurely, when
	 * the back-end actually <em>does</em> still need it may cause a host of other problems.
	 */
	void forciblyCloseTransactions();
}
