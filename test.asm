START:  LOC 100

; --- Load/Store ---
        LDA 0,0,NUM       ; Load NUM into R0
        LDR 1,0,NUM       ; Load NUM into R1
        STR 0,0,NUM       ; Store R0 back to NUM
        LDX 1,IDX          ; Load indexed
        STX 1,IDX          ; Store indexed

; --- Arithmetic/Logical ---
        ADD 0,1,0         ; R0 = R0 + R1
        SUB 1,0,1         ; R1 = R1 - R0
        MLT 0,1,0         ; Multiply
        DVD 1,0,1         ; Divide
        TRR 0,1,0         ; Test register
        AND 0,1,0         ; AND
        ORR 0,1,0          ; OR
        NOT 0,1           ; NOT

; --- Immediate instructions ---
        AIR 0,10          ; Add immediate to R0
        SIR 1,5           ; Subtract immediate from R1

; --- Transfer instructions ---
        JZ 0,0,LABEL1     ; Jump if zero
        JNE 1,0,LABEL2    ; Jump if not equal
        JCC 0,0,LABEL1    ; Jump conditional code
        JMA 0,0,LABEL2    ; Jump to address
        JSR 0,0,LABEL1    ; Jump to subroutine
        RFS 3              ; Return from subroutine
        SOB 1,0,LABEL1     ; Subtract one and branch
        JGE 0,0,LABEL2     ; Jump if greater or equal

; --- Misc ---
        TRAP 15            ; Trap call
        HLT                ; Halt program

; --- Data ---
NUM:    DATA 5
IDX:    DATA 2
LABEL1: DATA 0
LABEL2: DATA 1