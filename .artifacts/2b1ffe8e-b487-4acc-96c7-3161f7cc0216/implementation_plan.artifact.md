# MyYoutube App Implementation Plan

Met deze app wil ik youtube videos tonen van specifieke kanalen, bijhouden of ze bekeken zijn, thumbnails en spoilers beheren, en kunnen casten naar Chromecast.

## Proposed Changes

### Data Layer

#### [NEW] [ChannelEntity.kt](file:///home/sander/AndroidStudioProjects/MyYoutube/app/src/main/java/nl/baasmail/myyoutube/data/local/ChannelEntity.kt)
Database entiteit voor een toegevoegd YouTube kanaal met instellingen.
- `id`: String (YouTube Channel ID)
- `name`: String
- `showThumbnails`: Boolean
- `spoilerFilterKeywords`: String (komma-gescheiden lijst van keywords)

#### [MODIFY] [VideoEntity.kt](file:///home/sander/AndroidStudioProjects/MyYoutube/app/src/main/java/nl/baasmail/myyoutube/data/local/VideoEntity.kt)
Uitbreiden met extra velden voor status en kanaal-link.
- `channelId`: String
- `isWatched`: Boolean
- `publishedAt`: Long (timestamp)

#### [NEW] [ChannelDao.kt](file:///home/sander/AndroidStudioProjects/MyYoutube/app/src/main/java/nl/baasmail/myyoutube/data/local/ChannelDao.kt)
DAO voor het beheren van kanalen.

#### [MODIFY] [VideoDao.kt](file:///home/sander/AndroidStudioProjects/MyYoutube/app/src/main/java/nl/baasmail/myyoutube/data/local/VideoDao.kt)
Toevoegen van `markAsWatched` en filters.

#### [MODIFY] [AppDatabase.kt](file:///home/sander/AndroidStudioProjects/MyYoutube/app/src/main/java/nl/baasmail/myyoutube/data/local/AppDatabase.kt)
Toevoegen van `ChannelDao` en database versie verhogen indien nodig.

---

### Remote Layer

#### [MODIFY] [YouTubeApiService.kt](file:///home/sander/AndroidStudioProjects/MyYoutube/app/src/main/java/nl/baasmail/myyoutube/data/remote/YouTubeApiService.kt)
- Zoeken naar kanalen (voor toevoegen).
- Ophalen van video's per kanaal (gefilterd op type 'video', niet shorts).

---

### Business Logic

#### [NEW] [YouTubeRepository.kt](file:///home/sander/AndroidStudioProjects/MyYoutube/app/src/main/java/nl/baasmail/myyoutube/data/repository/YouTubeRepository.kt)
Coördineert data tussen de API en de lokale database.

---

### UI Layer (Compose)

#### [NEW] [HomeViewModel.kt](file:///home/sander/AndroidStudioProjects/MyYoutube/app/src/main/java/nl/baasmail/myyoutube/ui/home/HomeViewModel.kt)
Beheert de lijst met video's, past spoiler-filters toe en checkt bekeken status.

#### [NEW] [ChannelsViewModel.kt](file:///home/sander/AndroidStudioProjects/MyYoutube/app/src/main/java/nl/baasmail/myyoutube/ui/channels/ChannelsViewModel.kt)
Beheert het toevoegen en bewerken van kanalen en hun instellingen.

#### [NEW] UI Components
- `MainScreen.kt`: Scaffold met navigatie.
- `VideoListScreen.kt`: Lijst van video's met thumbnails (optioneel), titels (gefilterd), "bekeken" knop en Cast-icoon.
- `ChannelManagementScreen.kt`: Lijst van kanalen en toevoeg-interface.

---

### Chromecast Integration

#### [NEW] [CastManager.kt](file:///home/sander/AndroidStudioProjects/MyYoutube/app/src/main/java/nl/baasmail/myyoutube/cast/CastManager.kt)
Helper klasse voor het initialiseren en aansturen van de Chromecast sessie vanuit de UI.

## Verification Plan

### Automated Tests
- Unit tests voor `HomeViewModel` (spoiler filtering logica).
- Room DAO tests voor bekeken status.

### Manual Verification
- App starten, kanaal toevoegen.
- Instellingen wijzigen (thumbnails aan/uit) en effect in lijst controleren.
- Video markeren als bekeken en zien dat deze visueel veranderd of verdwijnt (afhankelijk van definitieve UI keuze).
- Casten naar een Chromecast device in het netwerk.

> [!IMPORTANT]
> Ik heb een YouTube API Key nodig voor de video-data. Ik zal een placeholder gebruiken (`API_KEY`) die je later in `local.properties` of een config bestand kunt zetten.
