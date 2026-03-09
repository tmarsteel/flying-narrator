<?php
declare(strict_types=1);

$data = simplexml_load_string(
  file_get_contents("php://stdin"),
) or die("failed to read input");
$targetIdx = intval($GLOBALS["argv"][1]);

$idx = 0;
foreach ($data->Document->Placemark as $node) {
  if (!kmlPlacemark_is_stage($node)) {
    continue;
  }
  if ($idx == $targetIdx) {
    $vectors = kmlPlacemark_to_euclid_vectors($node);
    echo json_encode([
      "vectors" => $vectors,
    ]);
    exit(0);
  }
  $idx++;
}
exit(1);

function kmlPlacemark_is_stage(SimpleXMLElement $placemark): bool {
  return !empty($placemark->LineString);
}
function kmlPlacemark_to_euclid_vectors(SimpleXMLElement $placemark): array {
  $coords = parse_kmlLineString($placemark->LineString->coordinates->__toString());
  $vectors = [];
  $previous = $coords[0];
  $coords = array_slice($coords, 1);
  foreach ($coords as $coord) {
    $vectors []= euclidian_vector_between_spatials($previous, $coord);
    $previous = $coord;
  }

  return $vectors;
}

/**
 * Returns a vector from $from to $to, that assumes the earth is flat.
 */
function euclidian_vector_between_spatials(Coordinate $from, Coordinate $to): V3 {
  // we are processing KML files, which are based on Google Earth
  // Google Earth+Maps assume the earth is a perfect sphere
  // alas, we make the same assumption here for correctness of the result

  $polar_circumference = 40075004;
  $equatorial_circumference = $polar_circumference;

  $polar_angular_distance = $to->lat - $from->lat;
  $equatorial_angular_distance = $to->long - $from->long;
  if ($equatorial_angular_distance < -180.0) {
    $equatorial_angular_distance += 360.0;
  } else if ($equatorial_angular_distance > 180.0) {
    $equatorial_angular_distance -= 360.0;
  }

  $polar_distance = $polar_angular_distance / 360.0 * $polar_circumference;
  $equatorial_distance = $equatorial_angular_distance / 360.0 * $equatorial_circumference;

  return new V3(
    $equatorial_distance,
    $polar_distance,
    $to->alt - $from->alt,
  );
}

function parse_kmlLineString(string $text): array {
  $coords = preg_split("/\\s+/", trim($text));
  return array_map(
    function ($coordText) {
      $parts = explode(",", $coordText);
      return new Coordinate(
        floatval($parts[0]),
        floatval($parts[1]),
        count($parts) > 2? floatval($parts[2]) : 0.0,
      );
    },
    $coords
  );
}

class V3 {
  function __construct(
    public float $x,
    public float $y,
    public float $z,
  ){}

  public function length(): float {
    return sqrt($this->x * $this->x + $this->y * $this->y + $this->z * $this->z);
  }
}

class Coordinate {
  function __construct(
    public float $lat,
    public float $long,
    public float $alt
  ){}
}
