# 🖥️ Computer System Simulator  

## 📘 Project Overview
This project is a semester-long design and implementation of a **C6461 computer system simulator**, developed in **Java**.  
It models all major components of a CPU and memory subsystem, providing a hands-on understanding of:
- **Instruction Set Architecture (ISA)**
- **Fetch–Decode–Execute cycles**
- **Memory addressing**
- **I/O device simulation**

The project is divided into progressive components, each building upon the previous, to gradually construct a complete and fully functional simulated machine.

---

## 👩‍💻 Team
### Group 10
- **Peiyan Liu**  
- **Aryan Saxena**  
- **Nikhil Arethiya**  
- **Alekya Kowta**

---

## 🧱 Project Structure

The project source is organized into modular Java packages, separating the core machine logic, assembler, and graphical user interface.

src/main/java/core: MachineState, MachineController, and CPU logic
src/main/core/cache: src Cache implementation (Project 2)
src/main/java/assembler: Assembler for ISA-compliant program loading
src/main/java/ui: SimulatorUI Assembler for ISA-compliant program loading


## Component 0: Assembler

**File:** `src/main/java/assembler/Assembler.java`

### Summary
Implements an assembler that translates symbolic assembly programs into **machine-readable load files**.  
Each line in the load file follows the format:

```<address> <value>```
Both in octal format, consistent with the ISA specification.

### Key Features
* **Data and Instruction Support:** Supports both data declarations (`Data`) and instruction assembly for the C6461 Instruction Set Architecture (ISA).
* **ISA Validation:** Validates all ISA-compliant fields, including:
    * `opcode`
    * `registers`
    * `index`
    * `indirect bit`
    * `5-bit address field`
* **Memory Enforcement:** Enforces the **12-bit memory space** constraint (addresses $0–4095$).
* **Load File Generation:** Generates load files compatible with the simulator’s **Initial Program Load (IPL)** process.

## Component I: Basic Machine (Core Implementation)

**Files:** `MachineState.java`, `MachineController.java`, `SimulatorUI.java`

This component implements the foundational C6461 machine model, capable of executing instructions in both single-step and continuous modes.

### Machine Architecture Details

#### Registers
* **General Purpose Registers (GPR):** GPR0–GPR3
* **Index Registers (X):** X1–X3
* **Internal Registers:** Program Counter (PC), Memory Address Register (MAR), Memory Buffer Register (MBR), Instruction Register (IR), Condition Code (CC), Machine Fault Register (MFR)

#### Memory
* **Main Memory Size:** 4096 words (12-bit addressing)
* **Word Size:** 16-bit

### Instruction Execution & Control
* **Cycle Implementation:** Complete **fetch–decode–execute cycle**.
* **Execution Modes:** Supports **Single-step** and continuous **Run** modes.

### User Interface (UI) Integration
* **Front Panel:** A **Swing-based** GUI displaying real-time register states and memory contents.
* **Manual Controls:** Allows for **manual register load** and **memory store** operations.
* **I/O Simulation:** Features **Console input/output devices** for I/O instruction simulation.

### Instruction Set Architecture (ISA) Coverage
* **Load/Store:** `LDR`, `STR`, `LDA`
* **Arithmetic:** `AMR`, `SMR`, `AIR`, `SIR`
* **Control:** `JZ`, `JNE`, `JCC`, `JMA`, `JSR`, `SOB`, `JGE`
* **Logical/Shift/Rotate** operations
* **I/O:** `IN`, `OUT`, `CHK`
* **Register-to-Register:** `MLT`, `DVD`, `TRR`, `AND`, `ORR`, `NOT`

## 💾 Component II: Memory and Cache Design

**File:** `src/main/java/core/cache/Cache.java`

This component introduces a fully associative cache subsystem, integrated with the machine’s main memory and visualization in the `MachineState` and `SimulatorUI`.

### Cache Configuration

Summary

| Parameter              | Value                      |
| ---------------------- | -------------------------- |
| **Size**               | 16 lines                   |
| **Block Size**         | 1 word                     |
| **Mapping**            | Fully associative          |
| **Replacement Policy** | FIFO (First-In, First-Out) |
| **Write Policy**       | Write-Through              |
| **Allocation Policy**  | Write-Allocate             |

### Implemented Features

#### Cache–Memory Coordination
* All reads and writes are routed through the **cache layer**.
* Implements **automatic load on read-miss** and **write-allocate on write-miss**.

#### Real-Time Visualization
* **GUI Display:** Cache lines are displayed in the simulator GUI.
* **Color Highlighting:** Uses colored highlighting (green/red) to indicate **read/write hits and misses** in real time.

#### Statistics Panel
Provides live counters for:
* Total Hits / Misses
* Read Hit/Miss counts
* Write Hit/Miss counts

#### Maintenance
* **FIFO Pointer Tracking:** Visually shows which cache line is next to be replaced.
* **Reset Integration:** The cache is automatically cleared during a machine reset (IPL).


### Component III: 
Execute All Instructions

### Component IV: 
Advanced Extensions (Choose One)   
Option A: Floating Point and Vector Operations  
Option B: Enhanced Scheduling
