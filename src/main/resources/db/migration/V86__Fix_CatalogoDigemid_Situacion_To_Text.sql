-- V86: situacion quedó como VARCHAR(50) tras V83; algunos valores del CSV exceden ese límite.
ALTER TABLE catalogo_digemid
    ALTER COLUMN situacion TYPE TEXT;
