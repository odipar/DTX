# Struck

What the style check strikes, under the AGENTS.md rule that strikes it.
The check, `org.dtx.style.HouseStyle`, reads this file and then every
document and code comment in the tree against it. `mvn test` runs it, and
so does

    java -cp target/classes org.dtx.style.HouseStyle

A section is one rule, and its heading is the rule's heading in AGENTS.md.
An entry is a name on a line of its own, then an indented block: the
pattern, a regular expression over lowered prose, on as many lines as it
takes; then `in:` lines, each a sample the pattern is in, and `not:` lines,
each a sample near it that the pattern is not in. HouseStyleTest reads
every sample back, so a pattern that drifts fails there rather than in
review. Add an entry to strike a construct, and take one off in the same
change that uses the construct.

Before the first rule, three entries are read differently. `names` lists
names a construct is spelled inside which are not that construct, each
blanked before a line is lowered: Windows is an operating system and
ST4_WINDOW an assembler symbol, where `window` the noun is struck, and
`decoder states` is the plural noun where `states` the verb is struck.
`carried` lists what the tree carries from another repository, by a
fragment of the path, and `own` what is read despite standing there, by the
end of the path. A carried copy follows its own tree's style.

names
    Windows
    ST4_WINDOW
    decoder states
    Decoder states

carried
    /org/st4/
    /dotnet/nt4/
    ST4_wrap.S
    /go/st4/

own
    beside.go
    packer.go

## Nothing acts on its own

Roles and abstractions doing what a person does: a writer promising, a
source implying, a reader wanting or knowing, a ring holding.

promising
    \bpromis(?:e|es|ed|ing)\b
    in: the writer promises a value
    not: a compromise between the two

guaranteeing
    \bguarantee[sd]?\b
    in: the header guarantees a count

implying
    \bimpl(?:y|ies|ied|ying)\b
    in: the source implies a width
    not: an implementation follows

can be told
    \bcan be told\b
    in: the reader can be told the width

roles stand
    \broles stand\b
    in: the roles stand as they were

it ruled
    \bit ruled\b
    in: it ruled the width out

it measured
    \bit measured\b
    in: it measured 400 bytes

answering
    \banswer(?:s|ed|ing)?\b
    in: the format answers a constraint

carrying, where a specification defines
    \bcarries\b
    in: the section carries the bits
    not: a carried copy

sits in
    \bsits in\b
    in: the value sits in a field

stand apart
    \bstand apart\b
    in: the two stand apart

stand as they were
    \bstand as they were\b
    in: the bits stand as they were

takes the machine with it
    \btakes the machine with it\b
    in: a fault takes the machine with it

a thing that says
    \bsays? (?:that|it|so)\b|\bbecause it says\b|\bsays what to take\b
    |\bspells? out\b
    in: the flag says that the column is packed
    in: the header spells out the count
    not: the flag marks the column

a fact that is a thing's own to give
    \bown to [a-z]+\b
    in: a length is the data set's own to define
    not: its own tree

consuming
    \bconsum(?:e|es|ed|ing)\b
    in: the verb consumes its operand
    not: the consumer reads it

understanding
    \bunderst(?:and|ands|anding|ood)\b
    in: a reader understands the stream

wanting
    \bwant(?:s|ed|ing)?\b
    in: the file wants a header
    not: an unwanted byte

knowing
    \bkn(?:ow|ows|own|owing|ew)\b
    in: the reader knows the width
    not: an unknown width

refusing
    \brefus(?:e|es|ed|ing|al)\b
    in: the reader refuses the file

holding
    \bh(?:old|olds|olding|eld)\b
    in: the ring holds a row
    not: the threshold

buying
    \b(?:buy|buys|bought|buying)\b
    in: a wider ring buys a table

paying
    \b(?:pays|paid|paying|pay for)\b
    in: a column pays for its width

asking for
    \bask(?:s|ed|ing)? for\b
    in: a table asks for the copy code
    not: a task for the reader

choosing
    \bch(?:oose|ooses|oosing|ose|osen)\b
    in: the packer chose a unit

agreeing
    \bagree(?:s|d|ing|ment)?\b
    in: the bytes agree

spending
    \bsp(?:end|ends|ent|ending)\b
    in: the refill spends a cycle

picking
    \bpick(?:s|ed|ing)?\b
    in: the flag picks the build

serving
    \bserv(?:e|es|ed|ing)\b
    in: one build serves every column
    not: the server

settling
    \bsettl(?:e|es|ed|ing)\b
    in: the table settles the period

trusting
    \btrust(?:s|ed|ing)?\b
    in: the reader trusts the header

letting
    \blets?\b
    in: the flag lets the decoder copy
    not: a bullet

## Say it once

The cleft: `X is what makes Y` is `X makes Y`. R3.5, R4.4 and R5.7 keep
`That is what DTXn is for`, so the bare `is what` is not struck.

the cleft
    \b(?:which|this) is what\b|\bis what lets\b
    in: which is what makes the row
    not: that is what DTX0 is for

## No flourish

the sweep
    \bwhatever\b|\bwhichever way\b|\bwhere it sits\b|\bstood still\b
    in: whatever value is written

the metaphor
    \ba tail\b|\bsliver|\bliterally\b|\bsmear|\bbears it out\b
    |\bpressure point\b|\bdoor left open\b|\bcover version\b|\bsmuggl
    |\bcatastroph
    in: leaves a tail no reader touches
    in: a catastrophic read

the verdict
    \bis deliberate\b|\bby design\b|\bon purpose\b|\basked properly\b
    |\bnot a shrug\b|\bmost of the point\b|\bthe answer to that\b
    |\bworth reading\b|\bthe ones that matter\b|\bthe whole point\b
    |\bmeaning\b
    in: this is deliberate
    in: it gives the shape a meaning

filler
    \bactually\b|\bsimply\b
    in: the reader simply steps

## Plain words

set-ness
    \bset-ness\b
    in: the set-ness of a column

a noun as a verb
    \bvendor(?:s|ed|ing)?\b
    in: the repository vendors a library

## One vocabulary

One word for a thing that has one: the ring a data set unpacks through.

a second word for the ring
    \b(?:container|window|buffer)s?\b
    in: the window the decoder reads through
    not: the ring

## The verb that says the action

`state` is the noun - a state block, a decoder state - and a document, a
header or a payload defines. A verb is negated, not its object.

state as a verb
    \bstat(?:ed|ing)\b|\bstates\b
    |\b(?:to|can|cannot|not|does|must|should|will|may) state\b
    |\bstate (?:what|which|whether|how)\b
    in: the payload states the ring
    in: the header is to state the count
    not: the state block and the decoder state
    not: the decoder states stand at 80

a verb negating its object
    \b(?!(?:this|thus|as|unless|its|yes|plus|minus|bytes)\b)
    (?:[a-z]+s|are|were|had|have|do|did|took|wrote|left|kept|ran|gave|made
    |read|need|take|write|name|give|keep|run|contain|carry|pack|touch|move
    |share|report|open|leave|check|test|refill|decode|change|cost|define
    |hold|pad|install|walk|fix) no [a-z`]
    in: an image alone defines no table
    in: the file has no flag
    in: there is no index
    not: an image itself does not define a table
    not: this no longer applies, and no row is read

a verb with nothing for an object
    \b(?!(?:this|thus|as|unless|its|yes|plus|minus|bytes)\b)
    (?:[a-z]+s|are|were|had|have|do|did|took|wrote|left|kept|ran|gave|made
    |read|need|take|write|name|give|keep|run|contain|carry|pack|touch|move
    |share|report|open|leave|check|test|refill|decode|change|cost|define
    |hold|pad|install|walk|fix) (?:nothing|none)\b
    in: a read refills nothing
    in: DTX0 has none
    not: nothing decodes it, and no row is read

## Shape

No em dash construct anywhere: a dash that must stay is a single `-`. The
en dash and the minus sign are struck with it.

an em dash
    [—–−]
    in: a dash — like this
    not: a dash - like this
