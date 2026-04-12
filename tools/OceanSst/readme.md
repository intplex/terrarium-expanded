# Ocean SST Asset Build

This folder builds the compact sea-surface-temperature runtime asset used by ocean biome fallback.

## Source dataset

- NOAA WOA23 temperature climatology
- Period: 1991-2020 climate normal (`decav91C0`)
- Field: annual mean `t_an`
- Depth: `0 m`
- Resolution: `1.00 degree`

Source NetCDF:

- `https://www.ncei.noaa.gov/data/oceans/woa/WOA23/DATA/temperature/netcdf/decav91C0/1.00/woa23_decav91C0_t00_01.nc`

## Build

1. Download the NetCDF file above to `tools/OceanSst/woa23_decav91C0_t00_01.nc`.
2. Run:

```bash
python tools/OceanSst/build_woa23_1deg_surface_sst_asset.py
```

Outputs:

- `common/src/main/resources/data/terrarium_expanded/ocean/woa23_sst_1deg_surface_annual_1991_2020.i16le`
- `common/src/main/resources/data/terrarium_expanded/ocean/woa23_sst_1deg_surface_annual_1991_2020.json`

## Notes

- Runtime asset is quantized int16 (`0.01 C` scale) and only `129,600` bytes.
- Missing coastal cells are filled from nearest valid ocean SST with longitude wrap handling.
