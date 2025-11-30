; closest_n_unrolled.asm
; IMPORTANT: Reserved memory addresses 000000..000005 are used by the machine and must not
; be executed or written by user programs. We explicitly set the program origin to 000006
; to avoid these reserved locations. Do not define labels/data that bind to 0..5.
; Reads N (multi-digit, signed), then reads N candidates (multi-digit, signed), then reads 1 target (multi-digit, signed).
; Numbers are separated by any non-digit delimiter (e.g., space/newline/comma). Optional leading '-' is supported.
; Uses a compact parser that accumulates base-10 via shifts (x10 = x8 + x2). OUT still uses R3.
; Memory map (decimal):
; CAND0..CAND8 -> 129..137 (octal 0201..0207)
; TEMP_DIFF -> 140 (octal 0210)
; MIN_DIFF -> 141 (0211)
; WINNER   -> 142 (0212)
; CNT, CNT_INIT -> 143,144 etc

; Program origin set beyond reserved addresses (0..5) and above low-constant window (<=31)
; NOTE: The assembler parses numeric literals as DECIMAL. Addresses printed in logs are OCTAL.
; We start at LOC 40 (decimal). Low constants at LOC 20 (decimal). Pointer tables at 300 and 340 (decimal).
LOC 40

; --- Bootstrap: initialize index registers ---
; X1 -> DATA BASE (500 decimal)  ; moved far from pointer tables at 300..339 and 340..
; X2 -> PTR TABLE 1 BASE (300 decimal)
; X3 -> PTR TABLE 2 BASE (340 decimal)
LDX 1,DBASE_LO
LDX 2,PTBASE1_LO
LDX 3,PTBASE2_LO

ENTRY:

; === Static-20 mode bootstrap ===
; Fixed-input mode: read exactly 20 candidates, then 1 target
; Initialize CNT_INIT=20, CNT=20, IDX=0, set MODE=1 (candidates), start parser

; Set CNT_INIT = 20 and CNT = 20
    LDR 0,1,16
    AIR 0,10
    AIR 0,10
    STR 0,1,11
    STR 0,1,10
; IDX = 0
    LDR 0,1,16
    STR 0,1,13
; MODE=1 (reading candidates)
    LDR 2,1,16
    AIR 2,1
    STR 2,1,8
    ; reset parser state and jump to RINT_LOOP
    LDR 0,1,16
    LDR 1,1,16
    LDR 2,1,16
    LDR 3,1,16
    JMA 2,1,1

RINT_LOOP:
    IN 3,0
    STR 3,1,14
    ; If no digit seen, allow leading '-'
    JZ 2,2,2,1
RINT_DIGIT_CHECK:
    LDR 3,1,14
    SMR 3,1,5
    JGE 3,2,4,1
    ; Not a digit: skip if no digit yet, else done
    JZ 2,2,1,1
    JMA 2,7,1

RINT_CHECK_SIGN:
    LDR 3,1,14
    SMR 3,1,6
    JZ 3,2,5,1
    JMA 2,3,1

RINT_SET_NEG:
    LDR 1,1,16
    AIR 1,1
    JMA 2,1,1

RINT_GE_ZERO:
    SIR 3,10
    JGE 3,2,6,1
    AIR 3,10
    ; seenDigit = 1
    LDR 2,1,16
    AIR 2,1
    ; Accumulate: R0 = R0*10 + digit
    STR 3,1,15
    STR 0,1,14
    LDR 0,1,14
    SRC 0,1,1,1
    STR 0,1,7
    LDR 0,1,14
    SRC 0,3,1,1
    AMR 0,1,7
    AMR 0,1,15
    JMA 2,1,1

RINT_NOT_DIGIT:
    JZ 2,2,1,1
    ; else done
RINT_DONE:
    ; Apply sign if needed
    JZ 1,2,8,1
    NOT 0
    AIR 0,1
RINT_POS:
    ; Dispatch by MODE (MIN_DIFF): 0->N_POST, 1->CAND_POST, 2->T_POST
    LDR 2,1,8
    JZ 2,2,9,1
    SIR 2,1
    JZ 2,2,11,1
    ; else target
    JMA 2,20,1

N_POST:
    ; Cap N to 5 and store CNT/CNT_INIT, then go to candidate loop
    STR 0,1,15       ; PRINT_TMP = N
    LDR 2,1,15
    SIR 2,5                 ; R2 = N - 5
    JGE 2,2,10,1
    ; else store N as-is
    LDR 0,1,15
    STR 0,1,10
    STR 0,1,11
    LDR 0,1,16
    STR 0,1,13
    ; Next: read candidates
    ; Set MODE=1 (CAND)
    LDR 2,1,16
    AIR 2,1
    STR 2,1,8
    ; Reset parser state and loop
    LDR 0,1,16
    LDR 1,1,16
    LDR 2,1,16
    JMA 2,1,1

N_CAP:
    LDR 0,1,16
    AIR 0,5
    STR 0,1,10
    STR 0,1,11
    LDR 0,1,16
    STR 0,1,13
    ; MODE=1 and restart parser for candidates
    LDR 2,1,16
    AIR 2,1
    STR 2,1,8
    LDR 0,1,16
    LDR 1,1,16
    LDR 2,1,16
    JMA 2,1,1

CAND_POST:
    ; Store parsed value R0 into CAND[IDX] using CADDRS table (two-step addressing)
    ; R3 = CADDRS base address
    LDR 3,0,CADDRS_LO
    ; R3 = CADDRS + IDX
    AMR 3,1,13
    ; CUR_CAND_PTR_LO <- M[CADDRS + IDX] (pointer to CANDi)
    STR 3,0,CUR_CAND_PTR_LO
    LDR 3,0,CUR_CAND_PTR_LO,1
    STR 3,0,CUR_CAND_PTR_LO
    ; M[CANDi] <- R0
    STR 0,0,CUR_CAND_PTR_LO,1
    JMA 2,17,1

STORE_C0:
STORE_C1:
STORE_C2:
STORE_C3:
STORE_C4:
AFTER_STORE:
    ; Increment IDX
    LDR 1,1,13
    AIR 1,1
    STR 1,1,13
    ; Decrement CNT and loop
    LDR 2,1,10
    SIR 2,1
    STR 2,1,10
    JNE 2,2,19,1

READS_DONE:
; === Read Target using unified parser ===
    ; MODE=2 (TARGET)
    LDR 2,1,16
    AIR 2,2
    STR 2,1,8
    ; Reset parser state and jump to RINT_LOOP
    LDR 0,1,16
    LDR 1,1,16
    LDR 2,1,16
    JMA 2,1,1

CAND_NEXT:
    ; Continue candidate loop using unified parser
    ; Reset parser state and set MODE=1
    LDR 2,1,16
    AIR 2,1
    STR 2,1,8
    LDR 0,1,16
    LDR 1,1,16
    LDR 2,1,16
    JMA 2,1,1

T_POST:
    STR 0,1,12

; If CNT_INIT == 0 then nothing to compare -> HLT
LDR 2,1,11
JZ 2,3,8,1

; Compare candidates in a compact loop (IDX 0..CNT_INIT-1)
    ; Initialize CADDRS_PTR_LO to the candidate-address table base and set MIN_DIFF/WINNER with first candidate
    LDR 0,0,CADDRS_LO
    STR 0,0,CADDRS_PTR_LO
    ; Load first candidate value via address table pointer (two-step indirection)
    ; Step 1: R3 <- M[ M[CADDRS_PTR_LO] ] yields pointer to CANDi
    LDR 3,0,CADDRS_PTR_LO,1
    ; Step 2: store pointer into a low slot and indirect again to fetch the value
    STR 3,0,CUR_CAND_PTR_LO
    LDR 3,0,CUR_CAND_PTR_LO,1
    STR 3,1,15
    LDR 0,1,15
    SMR 0,1,12
    JGE 0,2,21,1
    NOT 0
    AIR 0,1
MINPOS0:
    STR 0,1,8
    LDR 3,1,15
    STR 3,1,9

    ; advance address-table pointer to next candidate (CADDRS_PTR_LO = CADDRS_PTR_LO + 1)
    LDR 0,0,CADDRS_PTR_LO
    AIR 0,1
    STR 0,0,CADDRS_PTR_LO

    ; Set IDX = 1
    LDR 1,1,16
    AIR 1,1
    STR 1,1,13

COMPARE_LOOP:
    ; If IDX == CNT_INIT -> we're done
    LDR 1,1,13
    SMR 1,1,11
    JZ 1,3,0,1

    ; Load next candidate via address table pointer (two-step indirection)
    LDR 3,0,CADDRS_PTR_LO,1
    STR 3,0,CUR_CAND_PTR_LO
    LDR 3,0,CUR_CAND_PTR_LO,1
    STR 3,1,15
    LDR 0,1,15
    JMA 2,28,1

DO_COMPARE:
    ; R0 contains candidate value; compute absolute difference with TARGET
    SMR 0,1,12
    JGE 0,2,29,1
    NOT 0
    AIR 0,1
DIFFPOS:
    STR 0,1,7
    ; If TEMP_DIFF < MIN_DIFF then update
    LDR 1,1,8
    LDR 2,1,7
    SMR 2,1,8
    ; R2 = TEMP_DIFF - MIN_DIFF
    ; Tie-breaker: prefer later candidate on equal diff (<=)
    ; Convert tie (0) into negative by subtracting 1, so only strictly positive skips update
    SIR 2,1
    JGE 2,2,30,1     ; if (TEMP_DIFF - MIN_DIFF - 1) >= 0 => TEMP_DIFF > MIN_DIFF, skip update
    ; else (negative): Update MIN_DIFF and WINNER (use PRINT_TMP which holds this candidate)
    LDR 2,1,7
    STR 2,1,8
    LDR 3,1,15
    STR 3,1,9
    JMA 2,31,1
SKIP_UPDATE:
    ; nothing to do
CONT_COMPARE:
    ; Advance address-table pointer and IDX, then loop
    LDR 0,0,CADDRS_PTR_LO
    AIR 0,1
    STR 0,0,CADDRS_PTR_LO
    LDR 1,1,13
    AIR 1,1
    STR 1,1,13
    JMA 2,22,1

COMP_DONE:
    ; X3 already points to pointer-table base; no need to reload here
    ; Full multi-digit print in decimal
    ; R1 holds |WINNER|, R2=pow, R0 temp; uses READBUF/TEMP_DIFF/PRINT_TMP.
    ; Handle sign
    LDR 0,1,9
    JGE 0,3,1,1
    LDR 3,1,6
    OUT 3,1
    LDR 1,1,9
    NOT 1
    AIR 1,1
    JMA 3,2,1
PW_ABS:
    LDR 1,1,9
PW_ZCHK:
    ; If R1 == 0 -> print '0'
    SMR 1,1,16
    JNE 1,3,3,1
    LDR 3,1,5
    OUT 3,1
    HLT

PW_FINDPOW:
    ; R2 = 1
    LDR 2,1,16
    AIR 2,1
PW_POW_LOOP:
    ; R0 = 10 * R2 (via 8+2)
    STR 2,1,14
    LDR 0,1,14
    SRC 0,1,1,1
    STR 0,1,7       ; TEMP_DIFF = 2*R2
    LDR 0,1,14
    SRC 0,3,1,1             ; R0 = 8*R2
    AMR 0,1,7         ; R0 = 10*R2
    STR 0,1,15
    ; if (10*R2) <= R1 then R2 = 10*R2 and repeat
    STR 1,1,14         ; save R1
    LDR 0,1,14         ; R0 = R1
    SMR 0,1,15       ; R0 = R1 - 10*R2
    JGE 0,3,6,1
    ; else proceed with current R2
    JMA 3,5,1
PW_GROW:
    LDR 2,1,15       ; R2 = 10*R2
    JMA 3,4,1

PW_PRINT:
    ; loop: quotient = R1 / R2; remainder = R1 % R2
PW_PRINT_LOOP:
    STR 2,1,15       ; save pow
    STR 1,1,14         ; save R1
    LDR 0,1,14         ; R0 = R1
    LDR 2,1,15       ; R2 = pow
    DVD 0,2               ; R0=quotient digit, R2=remainder
    STR 0,1,7       ; save digit
    ; After DVD 0,2: quotient is in R0, remainder is in R1 (not R2)
    STR 1,1,14         ; save remainder
    LDR 1,1,14         ; R1 = remainder
    ; pow = pow / 10
    LDR 2,1,15       ; reload pow
    LDR 0,1,16
    AIR 0,10
    DVD 2,0               ; R2 = pow / 10
    ; print the saved digit
    LDR 0,1,7
    STR 0,1,15
    LDR 3,1,5
    AMR 3,1,15
    OUT 3,1
    ; Continue if pow != 0
    SMR 2,1,16
    JNE 2,3,7,1
    HLT

HALT_NOW:
    HLT

; --- Low constants for index bases (must be <=31) ---
LOC 20
DBASE_LO: Data 500
PTBASE1_LO: Data 300
PTBASE2_LO: Data 340
CADDRS_LO: Data CADDRS
CADDRS_PTR_LO: Data 0
CUR_CAND_PTR_LO: Data 0

; --- Pointer Tables (placed in higher memory; accessed via X2/X3 with I=1) ---
LOC 300
PTR_ENTRY: Data ENTRY
PTR_RINT_LOOP: Data RINT_LOOP
PTR_RINT_CHECK_SIGN: Data RINT_CHECK_SIGN
PTR_RINT_DIGIT_CHECK: Data RINT_DIGIT_CHECK
PTR_RINT_GE_ZERO: Data RINT_GE_ZERO
PTR_RINT_SET_NEG: Data RINT_SET_NEG
PTR_RINT_NOT_DIGIT: Data RINT_NOT_DIGIT
PTR_RINT_DONE: Data RINT_DONE
PTR_RINT_POS: Data RINT_POS
PTR_N_POST: Data N_POST
PTR_N_CAP: Data N_CAP
PTR_CAND_POST: Data CAND_POST
PTR_STORE_C0: Data STORE_C0
PTR_STORE_C1: Data STORE_C1
PTR_STORE_C2: Data STORE_C2
PTR_STORE_C3: Data STORE_C3
PTR_STORE_C4: Data STORE_C4
PTR_AFTER_STORE: Data AFTER_STORE
PTR_READS_DONE: Data READS_DONE
PTR_CAND_NEXT: Data CAND_NEXT
PTR_T_POST: Data T_POST
PTR_MINPOS0: Data MINPOS0
PTR_COMPARE_LOOP: Data COMPARE_LOOP
PTR_LOAD_C0: Data COMPARE_LOOP
PTR_LOAD_C1: Data COMPARE_LOOP
PTR_LOAD_C2: Data COMPARE_LOOP
PTR_LOAD_C3: Data COMPARE_LOOP
PTR_LOAD_C4: Data COMPARE_LOOP
PTR_DO_COMPARE: Data DO_COMPARE
PTR_DIFFPOS: Data DIFFPOS
PTR_SKIP_UPDATE: Data SKIP_UPDATE
PTR_CONT_COMPARE: Data CONT_COMPARE

LOC 340
PTR_COMP_DONE: Data COMP_DONE
PTR_PW_ABS: Data PW_ABS
PTR_PW_ZCHK: Data PW_ZCHK
PTR_PW_FINDPOW: Data PW_FINDPOW
PTR_PW_POW_LOOP: Data PW_POW_LOOP
PTR_PW_PRINT: Data PW_PRINT
PTR_PW_GROW: Data PW_GROW
PTR_PW_PRINT_LOOP: Data PW_PRINT_LOOP
PTR_HALT_NOW: Data HALT_NOW

; --- Data ---
; IMPORTANT: Place data at 500 (decimal) to avoid overlapping pointer table 1 (300..331).
LOC 500
; Candidate storage (initially zero)
CAND0: Data 65486    ; -50 (16-bit two's complement)
CAND1: Data 65511    ; -25
CAND2: Data 65526    ; -10
CAND3: Data 65531    ; -5
CAND4: Data 65535    ; -1

; ASCII constants
ASCII_0: Data 48
ASCII_MINUS: Data 45

; Working data (initialized to zero)
TEMP_DIFF: Data 0
MIN_DIFF: Data 0
WINNER: Data 0
CNT: Data 0
CNT_INIT: Data 0
TARGET: Data 0
IDX: Data 0

READBUF: Data 0
PRINT_TMP: Data 0
ZERO: Data 0

; Scratch for advancing X3 pointer through CADDRS

; Additional static candidates to total 20
CAND5:  Data 0
CAND6:  Data 1
CAND7:  Data 2
CAND8:  Data 3
CAND9:  Data 4
CAND10: Data 5
CAND11: Data 10
CAND12: Data 25
CAND13: Data 50
CAND14: Data 100
CAND15: Data 200
CAND16: Data 300
CAND17: Data 400
CAND18: Data 500
CAND19: Data 1000

; Table of candidate addresses (20 entries)
CADDRS:
    Data CAND0
    Data CAND1
    Data CAND2
    Data CAND3
    Data CAND4
    Data CAND5
    Data CAND6
    Data CAND7
    Data CAND8
    Data CAND9
    Data CAND10
    Data CAND11
    Data CAND12
    Data CAND13
    Data CAND14
    Data CAND15
    Data CAND16
    Data CAND17
    Data CAND18
    Data CAND19

; End
