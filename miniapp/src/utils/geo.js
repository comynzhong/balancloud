const EARTH_RADIUS = 6371000

function rad(deg) {
  return (deg * Math.PI) / 180
}

export function distanceMeters(lat1, lng1, lat2, lng2) {
  if ([lat1, lng1, lat2, lng2].some((v) => v === null || v === undefined || v === '')) {
    return null
  }
  const dLat = rad(Number(lat2) - Number(lat1))
  const dLng = rad(Number(lng2) - Number(lng1))
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(rad(Number(lat1))) * Math.cos(rad(Number(lat2))) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
  return Math.round(2 * EARTH_RADIUS * Math.asin(Math.sqrt(a)))
}

export function formatDistance(meters) {
  if (meters === null || meters === undefined) return ''
  if (meters < 1000) return `${meters}m`
  return `${(meters / 1000).toFixed(1)}km`
}

export function getLocation() {
  return new Promise((resolve) => {
    uni.getLocation({
      type: 'gcj02',
      success: (res) => resolve({ latitude: res.latitude, longitude: res.longitude }),
      fail: () => resolve(null)
    })
  })
}

export function sortByDistance(stations, coords) {
  const list = stations.map((item) => {
    const meters =
      coords && item.latitude !== null && item.longitude !== null
        ? distanceMeters(coords.latitude, coords.longitude, item.latitude, item.longitude)
        : null
    return { ...item, distance: meters }
  })
  list.sort((a, b) => {
    if (a.distance === null && b.distance === null) return a.name.localeCompare(b.name)
    if (a.distance === null) return 1
    if (b.distance === null) return -1
    return a.distance - b.distance
  })
  return list
}
