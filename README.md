# Cycles Detection

Plugin per Gephi 0.11.3, sviluppato per Java 17.

## Obiettivo

Analizza il grafo visibile e mostra testimonianze di cicli nelle sue componenti,
per grafi diretti e non diretti. Sono considerati anche self-loop e archi paralleli.

La ricerca usa DFS: le testimonianze non costituiscono l'enumerazione completa dei
cicli. Nei grafi diretti conteggio e distribuzione dipendono dall'ordine di visita;
nei grafi non diretti il numero riportato corrisponde al cycle rank, mentre la
distribuzione dipende dall'ordine. I grafi misti non sono supportati.

I risultati sono presentati in una tabella HTML con le lunghezze dei cicli; questa
vista sostituisce il precedente grafico.

## Sviluppo

Requisiti: JDK 17 o superiore, Maven 3.6.3 o superiore, Autoconf, Automake e `make`.
La compilazione produce bytecode compatibile con Java 17. Autotools funge da wrapper
per una build Maven NBM autonoma.

```sh
autoreconf -fi
./configure
make
make check
make dist
```

Il pacchetto NBM generato è
`GraphCyclesDetection/target/graph-cycles-detection-0.1.0-SNAPSHOT.nbm`.
Per una prova locale, selezionarlo nel Plugin Manager di Gephi.

I sette gruppi di test automatizzati sono passati su JVM 17. Coprono grafi reali,
grafi fino a 50.000 nodi, annullamento e gestione degli errori, lock, grafi filtrati,
self-loop, archi paralleli e comportamento dell'interfaccia senza stato persistente.

## Stato e gap

- **Supportato:** grafi diretti o non diretti; tutte le componenti del grafo visibile.
- **Non supportato:** grafi misti.
- **Non incluso:** enumerazione completa dei cicli e rilevamento di clique.
- **Non implementati:** evidenziazione nel grafo, esportazione e opzioni di ricerca.
  Sono gap noti, non impegni di roadmap.
- **Rilascio:** la licenza del plugin deve ancora essere confermata. Non è attribuita
  alcuna licenza; la distribuzione pubblica resta bloccata fino a tale conferma.
