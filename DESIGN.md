
# C6461 Assembler — Design Notes (Part 0)

## Object Model
- `Main`: CLI entry. `assemble <source.asm> [--out-list file] [--out-load file]`.
- `Assembler`: Two-pass assembler orchestrator.
  - **Pass 1**: Build symbol table + assign addresses (handles `LOC`, `Data`, labels).
  - **Pass 2**: Encode words and emit listing/load files.
- `TokenizedLine`: Parses each source line into `{label, directive|opcode, operands[], comment}`.
- `SymbolTable`: `Map<String,Integer>` for label ⇒ address (decimal).
- `Encoder`: Opcode map + per-format emitters (memory, immediate, reg-reg, shift/rotate, IO, TRAP, FP/Vector).
- `AssemblyResult`: Holds listing lines and load lines for writing.

## Formats Implemented
- **Memory**: `[OPC6][R2][IX2][I1][ADDR5]` — used by load/store, transfers, FP/Vector, `CNVRT`.
- **Immediate**: `[OPC6][R2][IMM8]` — `AIR`, `SIR`.
- **Reg-Reg**: `[OPC6][RX2][RY2][000000]` — `MLT`, `DVD`, `TRR`, `AND`, `ORR`, `NOT` (RY ignored for `NOT`).
- **Shift/Rotate**: `[OPC6][R2][L/R1][A/L1][COUNT4][0000]` — `SRC`, `RRC`.
- **I/O**: `[OPC6][R2][DEVID8]` — `IN`, `OUT`, `CHK` (devices 0..31 used).
- **TRAP**: `[OPC6][00][00][0][CODE5]` — trap code 0..15 in address field.

> Address field is 5 bits (0..31). The *effective address* calculation (indexing + indirection) is a **runtime concern** for the simulator; here we only assemble the 16-bit word.

## Listing/Load
- **Listing**: `AAAAAA OOOOOO <reconstructed source + comment>`, where `A`/`O` are 6-digit **octal**.
- **Load**: `AAAAAA OOOOOO` lines only (non-blank lines), 6-digit octal pairs.

## Notes/Assumptions
- `LOC n` is **decimal** in source; listing shows octal address/word.
- Trailing `,I` or `,1` sets the **indirect** bit in memory-format emitters.
- For `JCC`, the `cc` operand is placed in the **R** field (0..3).
- For `LDX/STX`, the first operand is the index register number **1..3**, placed in the **IX** field; `R` is 0.
- For `RFS`, the return code occupies the 5-bit address field; IX/I ignored.
- FP/Vector mnemonics are included so Part II/IV code can be assembled even before simulation is built.

## Minimal Tests
- Assemble `sample.asm` and compare against `expected.list` and `expected.load`.
- Add your own: `AIR 0,10`, `SIR 3,5`, `MLT 0,2`, `NOT 1`, `SRC 3,3,1,1`, `IN 0,0`, `TRAP 5`.
