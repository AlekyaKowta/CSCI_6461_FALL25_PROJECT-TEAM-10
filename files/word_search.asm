; paragraph_search.asm
; Program: Read a paragraph from file, print it, prompt for a word,
; search for the word, and print the word with sentence and word indices.
; Uses TRAP services:
;  - TRAP 0: Load file at R0; returns R1=len
;  - TRAP 1: Print memory from R0 length R1
;  - TRAP 2: Read a word into memory at R0; returns R1=len
;  - TRAP 3: Search paragraph (R0..R3) -> R4=sentence#, R5=word#

; Start code at safe location beyond reserved 0..5
LOC 40

; Setup base pointers (X1 for data, X2 for pointer table)
LDX 1,DBASE_LO
LDX 2,PTBASE1_LO

; Load paragraph from file into buffer at PARA_BUF
LDA 0,2,0,1     ; R0 <- &PARA_BUF (PTR_PARA_BUF)
TRAP 0               ; Load file, R1 <- length
STR 1,2,1,1     ; Save paragraph length (PTR_PARA_LEN)

; Print the paragraph
LDA 0,2,0,1
LDR 1,2,1,1
TRAP 1

; Newline
LDR 3,2,8,1
OUT 3,1

; Prompt for the word: "Enter word: "
LDA 0,2,11,1
LDR 1,2,10,1
AIR 1,12
TRAP 1

; newline after prompt
LDR 3,2,8,1      ; R3 <- '\n' via PTR_ASCII_NL
OUT 3,1

; Read a word into WORD_BUF
LDA 0,2,2,1
TRAP 2               ; R1 <- word length
STR 1,2,3,1

; Prepare search parameters and perform TRAP 3
LDA 2,2,2,1     ; R2 <- &WORD_BUF
LDR 3,2,3,1     ; R3 <- word length
LDA 0,2,0,1     ; R0 <- &PARA_BUF
LDR 1,2,1,1     ; R1 <- paragraph length
TRAP 3               ; R0=sentence#, R1=word#

; If not found (R0==0), print message and halt
JZ 0,0,NOT_FOUND_PTR,1

; Save results for later, because R0/R1 will be repurposed for printing
STR 0,2,21,1    ; SENT_NO (PTR_SENT_NO offset 21)
STR 1,2,22,1    ; WORD_NO (PTR_WORD_NO offset 22)

; Print result: "Word: " + word + " Sentence: " + R4 + " Word: " + R5
LDA 0,2,13,1
LDR 1,2,10,1
AIR 1,6
TRAP 1

; Print the word itself
LDA 0,2,2,1
LDR 1,2,3,1
TRAP 1

; newline after "Word: <word>"
LDR 3,2,8,1
OUT 3,1

; Print " Sentence: "
LDA 0,2,15,1
LDR 1,2,10,1
AIR 1,11
TRAP 1

; Print sentence number (saved)
LDR 0,2,21,1     ; load SENT_NO
JSR 0,0,PRINT_DEC_PTR,1

; newline after "Sentence: <n>"
LDR 3,2,8,1
OUT 3,1

; Print " Word: "
LDA 0,2,17,1
LDR 1,2,10,1
AIR 1,7
TRAP 1

; Print word number (saved)
LDR 0,2,22,1     ; load WORD_NO
JSR 0,0,PRINT_DEC_PTR,1

; Final newline and halt
LDR 3,2,8,1
OUT 3,1
HLT

NOT_FOUND:
LDA 0,2,19,1
LDR 1,2,10,1
AIR 1,9
TRAP 1
LDR 3,2,8,1
OUT 3,1
HLT

; -----------------------------
; Decimal print subroutine (non-negative R0)
; Prints decimal representation of R0 using OUT 3,1. Clobbers R0..R3.
PRINT_DEC:
    ; Save link register (R3) since JSR stores return address in R3
    STR 3,2,23,1
    ; If R0 == 0: print '0' and return
    STR 0,2,4,1
    LDR 0,2,4,1
    JNE 0,0,PD_NONZERO_PTR,1
    LDR 3,2,7,1
    OUT 3,1
    ; Restore link register and return
    LDR 3,2,23,1
    RFS 0

PD_NONZERO:
    ; DPTR <- &DIGITS
    LDA 3,2,5,1
    STR 3,0,DPTR_LO
    ; COUNT <- 0
    LDR 3,2,10,1
    STR 3,2,6,1
    ; Reload value to convert into R0
    LDR 0,2,4,1

PD_DIV_LOOP:
    ; Divide R0 by 10: quotient in R0, remainder in R1
    LDR 2,2,9,1
    DVD 0,2
    ; Convert remainder to ASCII: R1 += '0'
    AMR 1,2,7,1
    ; Store remainder char at *DPTR using indirect through DPTR_LO
    STR 1,0,DPTR_LO,1
    ; DPTR++
    LDR 3,0,DPTR_LO
    AIR 3,1
    STR 3,0,DPTR_LO
    ; COUNT++
    LDR 3,2,6,1
    AIR 3,1
    STR 3,2,6,1
    ; Continue if quotient != 0
    JNE 0,0,PD_DIV_LOOP_PTR,1

    ; Print digits in reverse
    ; DPTR-- to last stored char
    LDR 3,0,DPTR_LO
    SIR 3,1
    STR 3,0,DPTR_LO

PD_PRINT_LOOP:
    ; Load char at *DPTR and print
    LDR 3,0,DPTR_LO,1
    OUT 3,1
    ; DPTR--
    LDR 3,0,DPTR_LO
    SIR 3,1
    STR 3,0,DPTR_LO
    ; COUNT--
    LDR 3,2,6,1
    SIR 3,1
    STR 3,2,6,1
    JNE 3,0,PD_PRINT_LOOP_PTR,1
    ; Restore link register and return
    LDR 3,2,23,1
    RFS 0

; -----------------------------
; Low constants and pointer cells (must be <= 31)
LOC 20
DBASE_LO:       Data 500
PTBASE1_LO:     Data 300
PRINT_DEC_PTR:  Data PRINT_DEC
PD_NONZERO_PTR: Data PD_NONZERO
PD_DIV_LOOP_PTR: Data PD_DIV_LOOP
PD_PRINT_LOOP_PTR: Data PD_PRINT_LOOP
NOT_FOUND_PTR:  Data NOT_FOUND
DPTR_LO:        Data 0

; -----------------------------
; Data section
LOC 500
; Small variables and constants (kept separate from large buffers)
PARA_LEN:   Data 0
WORD_LEN:   Data 0
TEMP:       Data 0
DIGITS:     Data 0
            Data 0
            Data 0
            Data 0
            Data 0
            Data 0
            Data 0
            Data 0
            Data 0
            Data 0
DIGCOUNT:   Data 0

; Constants and strings
ASCII_0:    Data 48
ASCII_NL:   Data 10
TEN:        Data 10
ZERO:       Data 0

; Saved results
SENT_NO:    Data 0
WORD_NO:    Data 0
R3_SAVE:    Data 0

PROMPT:     Data 69   ; 'E'
            Data 110  ; 'n'
            Data 116  ; 't'
            Data 101  ; 'e'
            Data 114  ; 'r'
            Data 32   ; ' '
            Data 119  ; 'w'
            Data 111  ; 'o'
            Data 114  ; 'r'
            Data 100  ; 'd'
            Data 58   ; ':'
            Data 32   ; ' '
PROMPT_LEN: Data 12

WORD_LABEL:     Data 87   ; 'W'
                Data 111  ; 'o'
                Data 114  ; 'r'
                Data 100  ; 'd'
                Data 58   ; ':'
                Data 32   ; ' '
WORD_LABEL_LEN: Data 6

SENT_LABEL:     Data 32   ; ' '
                Data 83   ; 'S'
                Data 101  ; 'e'
                Data 110  ; 'n'
                Data 116  ; 't'
                Data 101  ; 'e'
                Data 110  ; 'n'
                Data 99   ; 'c'
                Data 101  ; 'e'
                Data 58   ; ':'
                Data 32   ; ' '
SENT_LABEL_LEN: Data 11

WORDNUM_LABEL:     Data 32   ; ' '
                   Data 87   ; 'W'
                   Data 111  ; 'o'
                   Data 114  ; 'r'
                   Data 100  ; 'd'
                   Data 58   ; ':'
                   Data 32   ; ' '
WORDNUM_LABEL_LEN: Data 7

NOTFOUND:     Data 78  ; 'N'
              Data 111 ; 'o'
              Data 116 ; 't'
              Data 32  ; ' '
              Data 102 ; 'f'
              Data 111 ; 'o'
              Data 117 ; 'u'
              Data 110 ; 'n'
              Data 100 ; 'd'
NOTFOUND_LEN: Data 9

; Large buffers placed away from constants to avoid overlap with TRAP 0 writes
LOC 700
PARA_BUF:   Data 0

LOC 900
WORD_BUF:   Data 0

; Pointer table for far addressing (IX=2 with I=1)
LOC 300
PTR_PARA_BUF:        Data PARA_BUF
PTR_PARA_LEN:        Data PARA_LEN
PTR_WORD_BUF:        Data WORD_BUF
PTR_WORD_LEN:        Data WORD_LEN
PTR_TEMP:            Data TEMP
PTR_DIGITS:          Data DIGITS
PTR_DIGCOUNT:        Data DIGCOUNT
PTR_ASCII_0:         Data ASCII_0
PTR_ASCII_NL:        Data ASCII_NL
PTR_TEN:             Data TEN
PTR_ZERO:            Data ZERO
PTR_PROMPT:          Data PROMPT
PTR_PROMPT_LEN:      Data PROMPT_LEN
PTR_WORD_LABEL:      Data WORD_LABEL
PTR_WORD_LABEL_LEN:  Data WORD_LABEL_LEN
PTR_SENT_LABEL:      Data SENT_LABEL
PTR_SENT_LABEL_LEN:  Data SENT_LABEL_LEN
PTR_WORDNUM_LABEL:   Data WORDNUM_LABEL
PTR_WORDNUM_LABEL_LEN: Data WORDNUM_LABEL_LEN
PTR_NOTFOUND:        Data NOTFOUND
PTR_NOTFOUND_LEN:    Data NOTFOUND_LEN
PTR_SENT_NO:         Data SENT_NO
PTR_WORD_NO:         Data WORD_NO
PTR_R3_SAVE:         Data R3_SAVE
